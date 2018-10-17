# scm-ssh-plugin

The SSH Plugin for SCM Manager enables SSH access to Git repositories managed 
within [SCM Manager](http://www.scm-manager.org/).

## About this version

This is a fork (with a minor fix) of the [original plugin](http://oss.aquenos.com/scm/scm-ssh-plugin/).
The original plugin [stopped working at a certain point in time](https://github.com/litesolutions/scm-ssh-plugin/wiki).
At [Lite Solutions](http://www.litesolutions.es) we needed certificate based SSH
access to our git repositories so we decided to fix it and release the fix.

### Requirements

As of a [bug](https://bitbucket.org/sdorra/scm-manager/issue/720/plug-in-installation-fails-only-in-linux) in SCM Manager 1.45 and earlier you should use the _[dist](https://github.com/litesolutions/scm-ssh-plugin/tree/master/dist)_ version 
with SCM Manager 1.46 or later. Or you could build yourself on a Linux system,
that might work as well.

## Documentation

The original documentation follows as copied when we forked the project.

Setting up the SSH plugin is quite simple. Just download the plugin file and 
add it using plugin manager in the SCM administrative web-interface.

After restarting SCM manager, you can configure the SSH plugin in the general 
configuration panel of SCM. The SSH keys accepted for a specific user can be 
configured by the administrator in the user panel or by the respective user 
herself using the "Edit my SSH Keys" menu item.

For connecting to a Git repository over SSH, use a URL like `ssh://<username>@<server>:<port>/git/<repository>`. 
The port is optional, if the SSH server is running on port 22. However, for 
most operating systems binding to ports below 1024 requires special privileges, 
so you might need to setup the Java servlet container in a special way, if you 
want to use the standard port. By default the SSH plugin uses port 8022.

If you run the SSH plugin in parallel to another SSH daemon and use the same IP 
address or hostname for connections, you should make sure that the same SSH 
host keys are used. Otherwise the SSH client will complain about mismatching 
host keys.

## Download

This program and the accompanying materials are made available under the terms 
of the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html). By 
downloading the software you agree to be bound the terms of this license.

The scm-ssh-plugin uses the SSH server implementation from the [Apache Mina](http://mina.apache.org/) 
project.

## Changelog

- Release 1.0.1 (May 6th, 2015):
	- Fixed compatibility with SCM Manager release 1.33 and higher.
- Release 1.0.0 (November 14th, 2012):
	- First public release of scm-ssh-plugin, intended for use with SCM Manager 
release 1.22.
