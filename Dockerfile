FROM sonatype/nexus3:3.8.0

ENV S3_BLOBSTORE_VERSION 1.2.1-SNAPSHOT
ENV NEXUS_HOME /opt/sonatype/nexus

COPY target/nexus-blobstore-s3-${S3_BLOBSTORE_VERSION}.jar ${NEXUS_HOME}/system/org/sonatype/nexus/nexus-blobstore-s3/${S3_BLOBSTORE_VERSION}/

USER root

RUN sed -i.bak \
  -e "/nexus-blobstore-file/a\\"$'\n'"<bundle>mvn:org.sonatype.nexus/nexus-blobstore-s3/${S3_BLOBSTORE_VERSION}</bundle>" \
  ${NEXUS_HOME}/system/org/sonatype/nexus/assemblies/nexus-base-feature/*/nexus-base-feature-*-features.xml \
  ${NEXUS_HOME}/system/org/sonatype/nexus/assemblies/nexus-core-feature/*/nexus-core-feature-*-features.xml

# RUN yum -y update && yum install -y python-setuptools && easy_install pip && pip install awscli

USER nexus