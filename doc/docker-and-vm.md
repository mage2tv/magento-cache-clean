## Docker & VMs

This document is about common issues that can happen when using the watcher in a
virtualized development setup, e.g. docker or vagrant.

In many situations the cache watcher "just works" when run inside the container.
For example, if you are using [Warden](https://warden.dev), you can run the utility
after running `warden shell`:
```
$ composer global require mage2tv/magento-cache-clean
$ ~/.composer/vendor/bin/cache-clean.js -w
```
And since the composer directory is mounted in the image, the installation only has to
be done once for all projects.

However, there are a couple of common issues that can arrise when working
with a virtualized environment in regards to the `cache-clean.js` watcher.

For example, when the node based watcher is run in a different container than
PHP based Magento, the file system path to the Magento directory might be
different for each container.

A similar scenario would be where the Magento directory is a local mount of a
directory exported from a virtual machine: the file system path to the Magento
base directory might be different in the VM and on the host system.

Another scenario could be that the Magento base directory is a NFS mount in a VM,
which does not support inotify events.

So for a number of reasons you might choose to run the watcher in a system that
is different from the system where Magento is running.

To enable such scenarios, two things might be necessary, depending on your
specific setup.

First, a cache `id_prefix` might need to be configured in the `app/etc/env.php`
file in Magento.
If no ID prefix is configured, Magento calculates one based on the Magento base path,
so it would be different on the host and the guest system.
Here is an example how that looks for the file cache storage:


```
    'cache' => [
        'frontend' => [
            'default' => [
                'id_prefix' => 'e74_'
            ],
            'page_cache' => [
                'id_prefix' => 'e74_',
                'backend_options' => [
                    'cache_dir' => 'page_cache'
                ]
            ]
        ]
    ],
```

You can also add the ID prefix for other cache storage backends.
The value of the ID prefix doesn't matter, as long as it's 3 alphanumeric
characters followed by an underscore.

The second thing that might prevent the watcher from running happens because
it runs PHP to get the Magento cache configuration and a list of modules and
themes.
The module and theme directories are listed as the file system path for the
system that PHP is running in.
But again, that might not match the file system where node is running.

To solve the issue, it is possible to generate a dump of the required
information in PHP by running the included `generate-cache-clean-config.php`
script.

The script assumes it is run in a Magento base directory, or the Magento
directory can be passed as an argument:

```bash
$ php vendor/mage2tv/magento-cache-clean/bin/generate-cache-clean-config.php
# or
$ php vendor/mage2tv/magento-cache-clean/bin/generate-cache-clean-config.php path/to/magento
```

The configuration dump is written to the file `var/cache-clean-config.json`.

When the watcher is run with the JSON file present, it will read the
information from the file instead of shelling out to PHP.

You can also use this to manually tweak what modules to watch
for changes. For example, you could choose exclude all core modules.


### More on Docker

The following comments from Dimitar IvanovTryzens might be helpful how to run
the watcher in a docker context:

* https://github.com/mage2tv/magento-cache-clean/issues/31#issuecomment-479660779
* https://github.com/mage2tv/magento-cache-clean/issues/31#issuecomment-480562053

I might include the contents from them into this document at one point, but for now I hope
a link is good enough.
