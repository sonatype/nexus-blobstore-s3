ARG NEXUS_VERSION=3.8.0

FROM maven:3-jdk-8-alpine AS build

ARG PROJECT_NAME=nexus-blobstore-s3

COPY . /${PROJECT_NAME}/
RUN cd /${PROJECT_NAME}/; mvn package -DskipTests=true;

ENV NEXUS_HOME /opt/sonatype/nexus

FROM sonatype/nexus3:${NEXUS_VERSION}

ARG PROJECT_NAME=nexus-blobstore-s3
ARG S3_BLOBSTORE_VERSION=1.2.1-SNAPSHOT

COPY --from=build /${PROJECT_NAME}/target/nexus-blobstore-s3-${S3_BLOBSTORE_VERSION}.jar ${NEXUS_HOME}/system/org/sonatype/nexus/nexus-blobstore-s3/${S3_BLOBSTORE_VERSION}/

USER root

RUN sed -i.bak \
  -e "/nexus-blobstore-file/a\\"$'\n'"<bundle>mvn:org.sonatype.nexus/nexus-blobstore-s3/${S3_BLOBSTORE_VERSION}</bundle>" \
  ${NEXUS_HOME}/system/org/sonatype/nexus/assemblies/nexus-base-feature/*/nexus-base-feature-*-features.xml \
  ${NEXUS_HOME}/system/org/sonatype/nexus/assemblies/nexus-core-feature/*/nexus-core-feature-*-features.xml

USER nexus
