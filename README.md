Nexus S3 Blobstores
===================

This project adds S3 backed blobstores to Sonatype Nexus 3.  It allows
Nexus to store the components and assets in Amazon AWS S3 instead of a
local filesystem.

Installing
----------

The S3 blobstore introduces one new dependency: the servicemix OSGI
AWS SDK bundle.  To install, Copy nexus-blobstore-s3-*.jar and the AWS
SDK bundle jar into the nexus/deploy subdirectory.

```mvn dependency:get -Dartifact=org.apache.servicemix.bundles:org.apache.servicemix.bundles.aws-java-sdk:1.11.92_1 -Ddest=.```

Start the bundle from the Nexus console:

```
bundle:list | grep nexus-blobstore-s3
bundle:start <bundleNumber>
```

Configuration
-------------

Log in as admin and create a new blobstore, selecting S3 as the type.
Enter the bucket name as the path.  You need valid AWS credentials in
`~/.aws/credentials`.

Now you can create repositories with your new S3 blobstore.

Todo
----

Still to do: proper configuration screen that allows AWS credentials to be entered (requires upstream changes).
