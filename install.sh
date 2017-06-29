#!/bin/bash -e

export NEXUS_VERSION=3.4.0-01
export NEXUS_HOME=../nexus-professional-${NEXUS_VERSION}

mkdir -p ${NEXUS_HOME}/system/org/sonatype/nexus/nexus-blobstore-s3/${NEXUS_VERSION}/
cp target/nexus-blobstore-s3-*.jar ${NEXUS_HOME}/system/org/sonatype/nexus/nexus-blobstore-s3/${NEXUS_VERSION}/

sed -i .bak  -e "/nexus-blobstore-file/a\\"$'\n'"<bundle>mvn:org.sonatype.nexus/nexus-blobstore-s3/${NEXUS_VERSION}</bundle>" ${NEXUS_HOME}/system/org/sonatype/nexus/assemblies/nexus-base-feature/*/nexus-base-feature-*-features.xml
sed -i .bak -e "/nexus-blobstore-file/a\\"$'\n'"<bundle>mvn:org.sonatype.nexus/nexus-blobstore-s3/${NEXUS_VERSION}</bundle>" ${NEXUS_HOME}/system/org/sonatype/nexus/assemblies/nexus-core-feature/*/nexus-core-feature-*-features.xml 
