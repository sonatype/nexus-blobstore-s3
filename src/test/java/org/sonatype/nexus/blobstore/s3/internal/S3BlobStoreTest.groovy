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
package org.sonatype.nexus.blobstore.s3.internal

import org.sonatype.nexus.blobstore.LocationStrategy
import org.sonatype.nexus.blobstore.api.BlobId
import org.sonatype.nexus.blobstore.api.BlobStoreConfiguration

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.BucketLifecycleConfiguration
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.services.s3.model.lifecycle.LifecycleFilter
import com.amazonaws.services.s3.model.lifecycle.LifecycleTagPredicate
import spock.lang.Specification

/**
 * {@link S3BlobStore} tests.
 */
class S3BlobStoreTest
    extends Specification
{

  AmazonS3Factory amazonS3Factory = Mock()

  LocationStrategy permanentLocationStrategy = Mock()

  LocationStrategy temporaryLocationStrategy =  Mock()

  S3BlobStoreMetricsStore storeMetrics = Mock()

  AmazonS3 s3 = Mock()

  S3BlobStore blobStore = new S3BlobStore(amazonS3Factory, permanentLocationStrategy, temporaryLocationStrategy, storeMetrics)

  def "Get blob"() {
    given: 'A mocked S3 setup'
      permanentLocationStrategy.location(_) >> { args -> args[0].toString() }
      amazonS3Factory.create(_) >> s3
      def config = new BlobStoreConfiguration()
      config.attributes = [s3: [bucket: 'mybucket']]
      def attributesS3Object = mockS3Object("""\
        |#Thu Jun 01 23:10:55 UTC 2017
        |@BlobStore.created-by=admin
        |size=11
        |@Bucket.repo-name=test
        |creationTime=1496358655289
        |@BlobStore.content-type=text/plain
        |@BlobStore.blob-name=test
        |sha1=eb4c2a5a1c04ca2d504c5e57e1f88cef08c75707
      """.stripMargin())
      def contentS3Object = mockS3Object('hello world')
      1 * s3.doesBucketExist('mybucket') >> true
      1 * s3.getBucketLifecycleConfiguration('mybucket') >> blobStore.makeLifecycleConfiguration(7)
      1 * s3.doesObjectExist('mybucket', 'metadata.properties') >> false
      1 * s3.doesObjectExist('mybucket', 'content/test.properties') >> true
      1 * s3.getObject('mybucket', 'content/test.properties') >> attributesS3Object
      1 * s3.getObject('mybucket', 'content/test.bytes') >> contentS3Object

    when: 'An existing blob is read'
      blobStore.init(config)
      blobStore.doStart()
      def blob = blobStore.get(new BlobId('test'))

    then: 'The contents are read from s3'
      blob.inputStream.text == 'hello world'
  }

  private mockS3Object(String contents) {
    S3Object s3Object = Mock()
    s3Object.getObjectContent() >> new S3ObjectInputStream(new ByteArrayInputStream(contents.bytes), null)
    s3Object
  }
}
