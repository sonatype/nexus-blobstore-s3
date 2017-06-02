/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
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

import javax.inject.Named;
import javax.inject.Provider;

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.common.base.Strings;

import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.ACCESS_KEY_ID_KEY;
import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.CONFIG_KEY;
import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.REGION_KEY;
import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.SECRET_ACCESS_KEY_KEY;

/**
 * Creates configured AmazonS3 clients.
 */
@Named
public class AmazonS3Factory
{

  public AmazonS3 create(final BlobStoreConfiguration blobStoreConfiguration) {
    AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();

    String accessKeyId = blobStoreConfiguration.attributes(CONFIG_KEY).get(ACCESS_KEY_ID_KEY, String.class);
    String secretAccessKey = blobStoreConfiguration.attributes(CONFIG_KEY).get(SECRET_ACCESS_KEY_KEY, String.class);
    if (!Strings.isNullOrEmpty(accessKeyId) && !Strings.isNullOrEmpty(secretAccessKey)) {
      AWSCredentials credentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);
      builder = builder.withCredentials(new AWSStaticCredentialsProvider(credentials));
    }

    String region = blobStoreConfiguration.attributes(CONFIG_KEY).get(REGION_KEY, String.class);
    if (!Strings.isNullOrEmpty(region)) {
      builder = builder.withRegion(region);
    }

    return builder.build();
  }
}
