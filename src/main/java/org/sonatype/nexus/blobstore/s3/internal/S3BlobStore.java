/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2017-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.blobstore.s3.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.concurrent.locks.Lock;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.BlobSupport;
import org.sonatype.nexus.blobstore.LocationStrategy;
import org.sonatype.nexus.blobstore.MetricsInputStream;
import org.sonatype.nexus.blobstore.StreamMetrics;
import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.blobstore.api.BlobAttributes;
import org.sonatype.nexus.blobstore.api.BlobId;
import org.sonatype.nexus.blobstore.api.BlobMetrics;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;
import org.sonatype.nexus.blobstore.api.BlobStoreException;
import org.sonatype.nexus.blobstore.api.BlobStoreMetrics;
import org.sonatype.nexus.blobstore.api.BlobStoreUsageChecker;
import org.sonatype.nexus.common.stateguard.Guarded;
import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.HashCode;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.iterable.S3Objects;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.transfer.TransferManager;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.cache.CacheLoader.from;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.FAILED;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.NEW;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.STARTED;
import static org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport.State.STOPPED;

/**
 * A {@link BlobStore} that stores its content on AWS S3.
 */
@Named(S3BlobStore.TYPE)
public class S3BlobStore
    extends StateGuardLifecycleSupport
    implements BlobStore
{
  public static final String BASEDIR = "blobs";

  public static final String TYPE = "S3";

  public static final String BLOB_CONTENT_SUFFIX = ".bytes";

  public static final String BLOB_ATTRIBUTE_SUFFIX = ".properties";

  public static final String CONFIG_KEY = "s3";

  public static final String BUCKET_KEY = "bucket";

  public static final String ACCESS_KEY_ID_KEY = "accessKeyId";

  public static final String SECRET_ACCESS_KEY_KEY = "secretAccessKey";

  public static final String SESSION_TOKEN_KEY = "sessionToken";

  public static final String ASSUME_ROLE_KEY = "assumeRole";

  public static final String REGION_KEY = "region";

  public static final String METADATA_FILENAME = "metadata.properties";

  public static final String TYPE_KEY = "type";

  public static final String TYPE_V1 = "s3/1";

  public static final String CONTENT_PREFIX = "content";

  public static final String TEMPORARY_BLOB_ID_PREFIX = "tmp$";

  private final AmazonS3Factory amazonS3Factory;

  private final LocationStrategy permanentLocationStrategy;

  private final LocationStrategy temporaryLocationStrategy;

  private BlobStoreConfiguration blobStoreConfiguration;

  private S3BlobStoreMetricsStore storeMetrics;

  private LoadingCache<BlobId, S3Blob> liveBlobs;

  private AmazonS3 s3;

  @Inject
  public S3BlobStore(final AmazonS3Factory amazonS3Factory,
                     @Named("volume-chapter") final LocationStrategy permanentLocationStrategy,
                     @Named("temporary") final LocationStrategy temporaryLocationStrategy,
                     final S3BlobStoreMetricsStore storeMetrics)
  {
    this.amazonS3Factory = checkNotNull(amazonS3Factory);
    this.permanentLocationStrategy = checkNotNull(permanentLocationStrategy);
    this.temporaryLocationStrategy = checkNotNull(temporaryLocationStrategy);
    this.storeMetrics = checkNotNull(storeMetrics);
  }

  @Override
  protected void doStart() throws Exception {
    // ensure blobstore is supported
    S3PropertiesFile metadata = new S3PropertiesFile(s3, getConfiguredBucket(), METADATA_FILENAME);
    if (metadata.exists()) {
      metadata.load();
      String type = metadata.getProperty(TYPE_KEY);
      checkState(TYPE_V1.equals(type), "Unsupported blob store type/version: %s in %s", type, metadata);
    }
    else {
      // assumes new blobstore, write out type
      metadata.setProperty(TYPE_KEY, TYPE_V1);
      metadata.store();
    }
    liveBlobs = CacheBuilder.newBuilder().weakValues().build(from(S3Blob::new));
    storeMetrics.setBucket(getConfiguredBucket());
    storeMetrics.setS3(s3);
    storeMetrics.start();
  }

  @Override
  protected void doStop() throws Exception {
    liveBlobs = null;
    storeMetrics.stop();
  }

  /**
   * Returns path for blob-id content file relative to root directory.
   */
  private String contentPath(final BlobId id) {
    return getLocation(id) + BLOB_CONTENT_SUFFIX;
  }

  /**
   * Returns path for blob-id attribute file relative to root directory.
   */
  private String attributePath(final BlobId id) {
    return getLocation(id) + BLOB_ATTRIBUTE_SUFFIX;
  }

  /**
   * Returns a path for a temporary blob-id content file relative to root directory.
   */
  private String temporaryContentPath(final BlobId id, final UUID suffix) {
    return temporaryLocationStrategy.location(id) + "." + suffix + BLOB_CONTENT_SUFFIX;
  }

  /**
   * Returns path for a temporary blob-id attribute file relative to root directory.
   */
  private String temporaryAttributePath(final BlobId id, final UUID suffix) {
    return temporaryLocationStrategy.location(id) + "." + suffix + BLOB_ATTRIBUTE_SUFFIX;
  }

  /**
   * Returns the location for a blob ID based on whether or not the blob ID is for a temporary or permanent blob.
   */
  private String getLocation(final BlobId id) {
    if (id.asUniqueString().startsWith(TEMPORARY_BLOB_ID_PREFIX)) {
      return CONTENT_PREFIX + "/" + temporaryLocationStrategy.location(id);
    }
    return CONTENT_PREFIX + "/" + permanentLocationStrategy.location(id);
  }

  @Override
  @Guarded(by = STARTED)
  public Blob create(final InputStream blobData, final Map<String, String> headers) {
    checkNotNull(blobData);

    return create(headers, destination -> {
        try (InputStream data = blobData) {
          MetricsInputStream input = new MetricsInputStream(data);
          TransferManager transferManager = new TransferManager(s3);
          transferManager.upload(getConfiguredBucket(), destination, input, new ObjectMetadata())
              .waitForCompletion();
          return input.getMetrics();
        } catch (InterruptedException e) {
          throw new BlobStoreException("error uploading blob", e, null);
        }
      });
  }

  @Override
  @Guarded(by = STARTED)
  public Blob create(final Path sourceFile, final Map<String, String> headers, final long size, final HashCode sha1) {
    throw new BlobStoreException("hard links not supported", null);
  }

  private Blob create(final Map<String, String> headers, final BlobIngester ingester) {
    checkNotNull(headers);

    checkArgument(headers.containsKey(BLOB_NAME_HEADER), "Missing header: %s", BLOB_NAME_HEADER);
    checkArgument(headers.containsKey(CREATED_BY_HEADER), "Missing header: %s", CREATED_BY_HEADER);

    // Generate a new blobId
    BlobId blobId;
    if (headers.containsKey(TEMPORARY_BLOB_HEADER)) {
      blobId = new BlobId(TEMPORARY_BLOB_ID_PREFIX + UUID.randomUUID().toString());
    }
    else {
      blobId = new BlobId(UUID.randomUUID().toString());
    }

    final String blobPath = contentPath(blobId);
    final String attributePath = attributePath(blobId);
    final S3Blob blob = liveBlobs.getUnchecked(blobId);

    Lock lock = blob.lock();
    try {
      log.debug("Writing blob {} to {}", blobId, blobPath);

      final StreamMetrics streamMetrics = ingester.ingestTo(blobPath);
      final BlobMetrics metrics = new BlobMetrics(new DateTime(), streamMetrics.getSha1(), streamMetrics.getSize());
      blob.refresh(headers, metrics);

      S3BlobAttributes blobAttributes = new S3BlobAttributes(s3, getConfiguredBucket(), attributePath, headers, metrics);

      blobAttributes.store();
      storeMetrics.recordAddition(blobAttributes.getMetrics().getContentSize());

      return blob;
    }
    catch (IOException e) {
      // Something went wrong, clean up the files we created
      deleteQuietly(attributePath);
      deleteQuietly(blobPath);
      throw new BlobStoreException(e, blobId);
    }
    finally {
      lock.unlock();
    }
  }

  @Override
  @Guarded(by = STARTED)
  public Blob copy(final BlobId blobId, final Map<String, String> headers) {
    Blob sourceBlob = checkNotNull(get(blobId));
    String sourcePath = contentPath(sourceBlob.getId());
    return create(headers, destination -> {
        s3.copyObject(getConfiguredBucket(), sourcePath, getConfiguredBucket(), destination);
        BlobMetrics metrics = sourceBlob.getMetrics();
        return new StreamMetrics(metrics.getContentSize(), metrics.getSha1Hash());
    });
  }

  @Nullable
  @Override
  @Guarded(by = STARTED)
  public Blob get(final BlobId blobId) {
    checkNotNull(blobId);

    final S3Blob blob = liveBlobs.getUnchecked(blobId);

    if (blob.isStale()) {
      Lock lock = blob.lock();
      try {
        if (blob.isStale()) {
          S3BlobAttributes blobAttributes = new S3BlobAttributes(s3, getConfiguredBucket(), attributePath(blobId).toString());
          boolean loaded = blobAttributes.load();
          if (!loaded) {
            log.warn("Attempt to access non-existent blob {} ({})", blobId, blobAttributes);
            return null;
          }

          if (blobAttributes.isDeleted()) {
            log.warn("Attempt to access soft-deleted blob {} ({})", blobId, blobAttributes);
            return null;
          }

          blob.refresh(blobAttributes.getHeaders(), blobAttributes.getMetrics());
        }
      }
      catch (IOException e) {
        throw new BlobStoreException(e, blobId);
      }
      finally {
        lock.unlock();
      }
    }

    log.debug("Accessing blob {}", blobId);

    return blob;
  }

  @Override
  @Guarded(by = STARTED)
  public boolean delete(final BlobId blobId, String reason) {
    // FIXME: do soft deletes
    return deleteHard(blobId);
  }

  @Override
  @Guarded(by = STARTED)
  public boolean deleteHard(final BlobId blobId) {
    checkNotNull(blobId);

    try {
      log.debug("Hard deleting blob {}", blobId);

      String attributePath = attributePath(blobId);
      S3BlobAttributes blobAttributes = new S3BlobAttributes(s3, getConfiguredBucket(), attributePath);
      Long contentSize = getContentSizeForDeletion(blobAttributes);

      String blobPath = contentPath(blobId);

      boolean blobDeleted = delete(blobPath);
      delete(attributePath);

      if (blobDeleted && contentSize != null) {
        storeMetrics.recordDeletion(contentSize);
      }

      return blobDeleted;
    }
    catch (IOException e) {
      throw new BlobStoreException(e, blobId);
    }
    finally {
      liveBlobs.invalidate(blobId);
    }
  }

  @Nullable
  private Long getContentSizeForDeletion(final S3BlobAttributes blobAttributes) {
    try {
      blobAttributes.load();
      return blobAttributes.getMetrics() != null ? blobAttributes.getMetrics().getContentSize() : null;
    }
    catch (Exception e) {
      log.warn("Unable to load attributes {}, delete will not be added to metrics.", blobAttributes, e);
      return null;
    }
  }


  @Override
  @Guarded(by = STARTED)
  public BlobStoreMetrics getMetrics() {
    return storeMetrics.getMetrics();
  }

  @Override
  @Guarded(by = STARTED)
  public synchronized void compact() {
    compact(null, null);
  }

  @Override
  @Guarded(by = STARTED)
  public synchronized void compact(@Nullable final BlobStoreUsageChecker inUseChecker, @Nullable final Logger logger) {
      // no-op
  }

  @Override
  public BlobStoreConfiguration getBlobStoreConfiguration() {
    return this.blobStoreConfiguration;
  }

  @Override
  public void init(final BlobStoreConfiguration configuration) {
    this.blobStoreConfiguration = configuration;
    try {
      this.s3 = amazonS3Factory.create(configuration);
      if (!s3.doesBucketExist(getConfiguredBucket())) {
        s3.createBucket(getConfiguredBucket());
      }
      setConfiguredBucket(getConfiguredBucket());
    }
    catch (Exception e) {
      throw new BlobStoreException("Unable to initialize blob store bucket: " + getConfiguredBucket(), e, null);
    }
  }

  private boolean delete(final String path) throws IOException {
    s3.deleteObject(getConfiguredBucket(), path);
    // note: no info returned from s3
    return true;
  }

  private void deleteQuietly(final String path) {
    s3.deleteObject(getConfiguredBucket(), path);
  }

  private void move(final String source, final String target) throws IOException {
    s3.copyObject(getConfiguredBucket(), source, getConfiguredBucket(), target);
    s3.deleteObject(getConfiguredBucket(), source);
  }

  private void setConfiguredBucket(final String bucket) {
    blobStoreConfiguration.attributes(CONFIG_KEY).set(BUCKET_KEY, bucket);
  }

  private String getConfiguredBucket() {
    return blobStoreConfiguration.attributes(CONFIG_KEY).require(BUCKET_KEY).toString();
  }

  /**
   * Delete files known to be part of the S3BlobStore implementation if the content directory is empty.
   */
  @Override
  @Guarded(by = {NEW, STOPPED, FAILED})
  public void remove() {
    try {
      boolean contentEmpty = s3.listObjects(getConfiguredBucket(), CONTENT_PREFIX + "/").getObjectSummaries().isEmpty();
      if (contentEmpty) {
        S3PropertiesFile metadata = new S3PropertiesFile(s3, getConfiguredBucket(), METADATA_FILENAME);
        metadata.remove();
        storeMetrics.remove();
        s3.deleteBucket(getConfiguredBucket());
      }
      else {
        log.warn("Unable to delete non-empty blob store content directory in bucket {}", getConfiguredBucket());
      }
    }
    catch (IOException e) {
      throw new BlobStoreException(e, null);
    }
  }

  class S3Blob
      extends BlobSupport
  {
    S3Blob(final BlobId blobId) {
      super(blobId);
    }

    @Override
    public InputStream getInputStream() {
      S3Object object = s3.getObject(getConfiguredBucket(), contentPath(getId()));
      return object.getObjectContent();
    }
  }

  private interface BlobIngester {
    StreamMetrics ingestTo(final String destination) throws IOException;
  }

  @Override
  public Stream<BlobId> getBlobIdStream() {
    Iterable<S3ObjectSummary> summaries = S3Objects.withPrefix(s3, getConfiguredBucket(), CONTENT_PREFIX);
    return StreamSupport.stream(summaries.spliterator(), false)
      .map(S3ObjectSummary::getKey)
      .map(key -> key.substring(key.lastIndexOf('/') + 1, key.length()))
      .filter(filename -> filename.endsWith(BLOB_ATTRIBUTE_SUFFIX) && !filename.startsWith(TEMPORARY_BLOB_ID_PREFIX))
      .map(filename -> filename.substring(0, filename.length() - BLOB_ATTRIBUTE_SUFFIX.length()))
      .map(BlobId::new);
  }

  @Override
  public BlobAttributes getBlobAttributes(final BlobId blobId) {
    try {
      S3BlobAttributes blobAttributes = new S3BlobAttributes(s3, getConfiguredBucket(), attributePath(blobId));
      return blobAttributes.load() ? blobAttributes : null;
    }
    catch (IOException e) {
      log.error("Unable to load S3BlobAttributes for blob id: {}", blobId, e);
      return null;
    }
  }
}
