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

import javax.inject.Named;

import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.common.base.Strings;

import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.ACCESS_KEY_ID_KEY;
import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.ASSUME_ROLE_KEY;
import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.CONFIG_KEY;
import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.REGION_KEY;
import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.SECRET_ACCESS_KEY_KEY;
import static org.sonatype.nexus.blobstore.s3.internal.S3BlobStore.SESSION_TOKEN_KEY;

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

      AWSCredentials credentials;
      String sessionToken = blobStoreConfiguration.attributes(CONFIG_KEY).get(SESSION_TOKEN_KEY, String.class);
      if (!Strings.isNullOrEmpty(sessionToken)) {
        credentials = new BasicSessionCredentials(accessKeyId, secretAccessKey, sessionToken);
      }
      else {
        credentials = new BasicAWSCredentials(accessKeyId, secretAccessKey);
      }

      AWSCredentialsProvider credentialsProvider;
      String assumeRole = blobStoreConfiguration.attributes(CONFIG_KEY).get(ASSUME_ROLE_KEY, String.class);
      if (!Strings.isNullOrEmpty(assumeRole)) {
        credentialsProvider = new STSAssumeRoleSessionCredentialsProvider.Builder(assumeRole, "nexus-s3-session")
          .withLongLivedCredentials(credentials).build();
      }
      else {
        credentialsProvider = new AWSStaticCredentialsProvider(credentials);
      }
      builder = builder.withCredentials(credentialsProvider);
    }

    String region = blobStoreConfiguration.attributes(CONFIG_KEY).get(REGION_KEY, String.class);
    if (!Strings.isNullOrEmpty(region)) {
      builder = builder.withRegion(region);
    }

    return builder.build();
  }
}
