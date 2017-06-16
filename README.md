Nexus Repository S3 Blobstores
==============================

This project adds S3 backed blobstores to Sonatype Nexus Repository 3.  It allows
Nexus Repository to store the components and assets in Amazon AWS S3 instead of a
local filesystem.

Contribution Guidelines
-----------------------

Go read [our contribution guidelines](/.github/CONTRIBUTING.md) to get a bit more familiar with how
we would like things to flow.

Installing
----------

To install, Copy nexus-blobstore-s3-*.jar and the AWS SDK bundle jar
into the nexus/deploy subdirectory.

Start the bundle from the Nexus Repository console:

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
