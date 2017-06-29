<!--

    Sonatype Nexus (TM) Open Source Version
    Copyright (c) 2017-present Sonatype, Inc.
    All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.

    This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
    which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.

    Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
    of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
    Eclipse Foundation. All other trademarks are the property of their respective owners.

-->
Nexus Repository S3 Blobstores
==============================

[![Join the chat at https://gitter.im/sonatype/nexus-developers](https://badges.gitter.im/sonatype/nexus-developers.svg)](https://gitter.im/sonatype/nexus-developers?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This project adds S3 backed blobstores to Sonatype Nexus Repository 3.  It allows
Nexus Repository to store the components and assets in Amazon AWS S3 instead of a
local filesystem.

Contribution Guidelines
-----------------------

Go read [our contribution guidelines](/.github/CONTRIBUTING.md) to get a bit more familiar with how
we would like things to flow.

Installing
----------

See `install.sh`.  This copies the nexus-blobstore-s3 jar file to the
right place and updates the configuration files.  Use at your own
risk.

Alternatively, copy nexus-blobstore-s3-*.jar and the AWS SDK bundle
jar into the nexus/deploy subdirectory.

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

Now you can create repositories with your new S3 blobstore.  The UI
isn't quite done -- for now, enter the bucket name as the path.

Todo
----

Still to do: proper configuration screen that allows AWS credentials
to be entered (requires upstream changes).  See the config-ui branch
in git.

The Fine Print
--------------

It is worth noting that this is **NOT SUPPORTED** by Sonatype, and is a contribution of ours
to the open source community (read: you!)

Remember:

* Use this contribution at the risk tolerance that you have
* Do NOT file Sonatype support tickets related to S3 support
* DO file issues here on GitHub, so that the community can pitch in

Phew, that was easier than I thought. Last but not least of all:

Have fun creating and using this plugin and the Nexus platform, we are glad to have you here!

Getting help
------------

Looking to contribute to our code but need some help? There's a few ways to get information:

* Chat with us on [Gitter](https://gitter.im/sonatype/nexus-developers)
* Check out the [Nexus3](http://stackoverflow.com/questions/tagged/nexus3) tag on Stack Overflow
* Check out the [Nexus Repository User List](https://groups.google.com/a/glists.sonatype.com/forum/?hl=en#!forum/nexus-users)
