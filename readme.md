> "You know, hope is a mistake. If you can't fix what's broken, you'll, uh... you'll go insane." - Max Rockatansky

# Magento 2 Cache Clean

A faster drop in replacement for `bin/magento cache:clean` with a file watcher.

The file watcher automatically cleans affected cache types in the Magento 2
cache during development.
For example, if you make a change to a template, it only cleans the
`block_html` and `full_page` caches, not the `config` or `layout` caches.

The project is only tested on MacOS and Linux.
Please report bugs by opening an issue on the GitHub issue tracker.


## Features

* Supports file, redis and varnish cache backends
* Removes affected generated code classes when a source file is changed
* Zero configuration, all required information is read from `app/etc/env.php`
* Hotkeys for quick cache flushes while the watcher is running

## Installation & Updating

Installation:

``` shell
composer require --dev mage2tv/magento-cache-clean
```

Update:

``` shell
composer remove --dev mage2tv/magento-cache-clean
composer require --dev mage2tv/magento-cache-clean
```

## Usage

In your Magento directory, run `vendor/bin/cache-clean.js --watch`

Press `Ctrl-C` to exit the watcher process.

The script can be used as a faster drop in replacement of `bin/magento cache:clean`,
too. For example:

``` shell
vendor/bin/cache-clean.js config full_page
```
(It's quicker because the start up time of `bin/magento` is so slow.)

There are several options to customize the behavior:

```
vendor/bin/cache-clean.js --help
Sponsored by https://www.mage2.tv

Usage: cache-clean.js [options and flags] [cache-types...]
Clean the given cache types. If none are given, clean all cache types.

--directory|-d <dir>    Magento base directory
--watch|-w              Watch for file changes
--verbose|-v            Display more information
--debug|-vv             Display too much information
--silent|-s             Display less information
--version               Display the version
--help|-h               This help message
```

Usually I run the command once with the `--watch` switch when I start
development, and when I make a change that isn't automatically detected (yet),
I run `vendor/bin/cache-clean.js` with the given cache types as a drop in
replacement for `bin/magento cache:clean`.

### Hotkeys

When the watcher is running, segments of the cache can be cleaned with
individual keystrokes:

|Key|Cache Segment(s)|
|---|----------------|
|`c`| `config` |
|`b`| `block_html` |
|`l`| `layout` |
|`f`| `full_page` |
|`a`| (a for all) |
|`v`| (v for view) `block_html`, `layout`, `full_page` |
|`t`| `translate` |

There also are hotkeys to clean the static assets in the `A`dminhtml or
the `F`rontend area or clean the `G`enerated code directory.


### Prerequisites:

* `node.js` (built on 10.8, but should work with older 8.x versions, too).
* it probably is a good idea to turn on all Magento caches
  `bin/magento cache:enable` to get the full benefit.

## Rationale

This utility aims to improve the Magento developer experience by shortening the
feedback loop during development through automating the removal of affected
cache sections after file changes.

**Assumptions:**

1. Magento uses caching a lot and is faster when the caches are warm.
2. As a developer I want a quick feedback loop.
3. Rebuilding the cache takes longer than cleaning the cache

To support the above assumptions, I want to only clean the cache segments I
really have to after making some changes.

For example, if I make a change to a template, I only want to flush the
`block_html` and `full_page` caches, not the `config` or `layout` caches.

Thinking about what cache types need to be cleaned after a change and typing the
exact command takes time, and it also gets very repetitive, so many developers
simply nuke the whole cache after every change.

Automating selective cache cleaning improves the developer experience.


## Known issues

* Currently the watcher has to be restarted after a new theme is added so it
  is added to the watchlist.

* Changes to files on NFS mounts (e.g. in vagrant) do not trigger the watches.
  Depending on a given setup, it might be possible to run the watcher on the
  host system instead.

* If you run the task in PHPStorm and the hotkeys are not working, search for
  actions by pressing `STRG+SHIFT+A`, then search for "registry...", then
  enable `nodejs.console.use.terminal` and restart the watcher process.

* Not tested a lot on Windows, please open an issue if you want to contribute.

* If you run into the error `Error NOSPC` on Linux, run the command:

``` shell
echo fs.inotify.max_user_watches=524288 | sudo tee -a /etc/sysctl.conf && sudo sysctl -p
```

## Docker & VMs

This section is about common issues that can happen when using the watcher in a
virtualized development setup.

There are a couple of issues that can arrise when working with a virtualized
environment in regards to the `cache-clean.js` watcher.

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
        'page_cache' => [
            'id_prefix' => 'a04_',
            'cache_dir' => 'page_cache',
        ],
        'default' => [
            'id_prefix' => 'a04_'
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

I might extract them into a separate document at one point, but for now I hope
that is enough.

## Building

The tool is written in ClojureScript.
To build, install Clojure 1.9 or later (e.g. `brew install clojure`) and run

```shell
$ clj -m figwheel.main -O advanced -bo build
$ chmod +x bin/cache-clean.js
```

## Thanks

Thanks to [Mage2 TV](https://www.mage2.tv/) for sponsoring the development of
this tool.

This script was inspired by [Timon de Groot](https://twitter.com/TimonGreat)'s
[blog post](https://blog.timpack.org/speed-up-magento-development) where he
describes the idea to use a file watcher in PHPStorm to call `redis-cli` to
clear the complete cache whenever a XML file is modified.
The only downside of that solution is that it always flushes the full cache and
only works with redis.

Thank you also to everybody who gave feedback, shared ideas and helped test new
features! This tool would be impossible without you!

## Copyright & License

Copyright 2019 by Vinai Kopp, distributed under the BSD-3-Clause license (see
the LICENSE file in this repository).
