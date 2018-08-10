# Magento 2 Cache Cleaner

This project is a file watcher to automate selectively cleaning affected cache
types in the Magento 2 file cache backend during development.

The project is very young and only tested on MacOS.
Please report bugs by opening an issue on the GitHub issue tracker.


## Installation

TODO: make installable with composer


## Usage

Prerequisites: `node.js` (built on 10.8, but should work with older versions, too).

1. Turn all on caches with `bin/magento cache:enable`
2. Run `node vendor/bin/cache-clean.js -w` from within your Magento project.

Press Ctrl-C to quit the watcher process.

The script can be used as a quicker version of `bin/magento cache:clean`, too.
For example:

``` shell
vendor/bin/clean-cache config page_page
```
(It's quicker because the start up time of `bin/magento` is a lot slower.)

There are several options to customize the behavior:

```
bin/cache-clean.js --help
Sponsored by https://www.mage2.tv

Usage: clean-cache.js [options and flags] [cache-types...]
Clear the given cache types. If none are given, clear all cache types.

--directory|-d <dir>    Magento base directory
--watch|-w              Watch for file changes
--verbose|-v            Display cleared cache types
--shout|-vv             Display more info
--debug|-vvv            Display too much information
--help|-h               This help message
```

Usually I run the command once with the `--watch` switch when I start
development, and when I make a change that isn't automatically deteceted (yet),
I run `bin/clean-cache.js` with the given cache types as a drop in replacement
for `bin/magento cache:clean`.


## Rationale

Two facts:

1. Magento uses a lot of caching.
2. As a developer I want a quick feedback loop.

Either I disable caches or I have to clean caches frequently after
I make some changes so I can see my changes take effect.
Both options make for a slow feedback loop.

The process of rebuilding the cache contents (i.e. the reload) takes more time
than the cleaning of cache segments. Because of this, cleaning only the affected
sections of the cache can be used to shorten the feedback loop.

But cleaning only parts of a cache with a careful - for example with
`bin/magento cache:clean layout page_cache` takes longer than simply running
`rm -r var/cache var/page_cache`

This project aims to help Magento developers to spend less time cleaning the
required cache types.


## Known issues

Currently the watcher has to be restarted when a new module or theme is added.

On linux recursive file watches are not supported, so a given directory branch
is scanned and all child directories are added to the watch list. That part works.
But when new directories are created, they are added to the watch list on the fly.
That part of the code needs testing.


## Building

The tool is written in ClojureScript.
To build, install Clojure 1.9 or later and run

```shell
$ clj -m figwheel.main -O advanced -bo build
$ chmod +x bin/clean-cache.js
```

(Installing Clojure is simple - e.g. on a Mac it's `brew install clojure`)


## Sponsorship

Thanks to [Mage2 TV](https://www.mage2.tv/) for sponsoring the development of this tool.


## TODO

* The config cache should be cleaned for controllers once and only if they contain
a controller class declaration, i.e. they are picked up by Magento as a
controller for a route.

* Add support for the REDIS cache storage backend


## Copyright & Licence

Copyright 2018 by Vinai Kopp, distributed under the BSD-3-Clause license (see
the LICENSE file in this repository).
