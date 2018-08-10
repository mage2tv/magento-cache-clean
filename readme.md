# Magento 2 Cache Clean

A faster drop in replacement for `bin/magento cache:clean` with a file watcher.

The file watcher automates selectively cleaning affected cache
types in the Magento 2 file cache backend during development.

The project is very young and only tested on MacOS.
Please report bugs by opening an issue on the GitHub issue tracker.


## Installation & Upgrading

Installation:

``` shell
composer require --dev mage2tv/magento-cache-clean`
```


Upgrade:

``` shell
composer update mage2tv/magento-cache-clean
```


## Usage

Run `vendor/bin/cache-clean.js --watchw` from within your Magento directory.

Press Ctrl-C to exit the watcher process.

The script can be used as a faster version of `bin/magento cache:clean`, too.
For example:

``` shell
vendor/bin/cache-clean.js config page_page
```
(It's quicker because the start up time of `bin/magento` is a lot slower.)

There are several options to customize the behavior:

```
bin/cache-clean.js --help
Sponsored by https://www.mage2.tv

Usage: cache-clean.js [options and flags] [cache-types...]
Clear the given cache types. If none are given, clear all cache types.

--directory|-d <dir>    Magento base directory
--watch|-w              Watch for file changes
--verbose|-v            Display cleared cache types
--shout|-vv             Display more info
--debug|-vvv            Display too much information
--help|-h               This help message
```

Usually I run the command once with the `--watch` switch when I start
development, and when I make a change that isn't automatically detected (yet),
I run `bin/cache-clean.js` with the given cache types as a drop in replacement
for `bin/magento cache:clean`.


### Prerequisites:

* `node.js` (built on 10.8, but should work with older versions, too).
* it probably is a good idea to turn on all Magento caches
  `bin/magento cache:enable` to get the full benefit.


## Rationale

Assumptions:

1. Magento uses caching a lot and is faster when the caches are warm.
2. As a developer I want a quick feedback loop.
3. Rebuilding the cache is slower than cleaning the cache

To support the above assumptions, I want to only remove the cache segments I
really have to remove after making some changes.
For example, if I make a change to a template, I only want to flush the
`block_html` and `full_page` cache, not the `config` cache.

Thinking about what cache types need to be cleaned after a change and typing the
exact command takes time, and gets very repetitive, so many developers simply
nuke the whole cache after every change.

This utility aims to improve the Magento developer experience by shortening the
feedback loop during development through automating the removal of affected
cache sections after file changes.


## Known issues

Currently the watcher has to be restarted when a new module or theme is added.

On Linux recursive file watches are not supported, so a given directory branch
is scanned and all child directories are added to the watch list. That part works.
But when new directories are created, they are added to the watch list on the fly.
That part of the code needs testing.


## Building

The tool is written in ClojureScript.
To build, install Clojure 1.9 or later and run

```shell
$ clj -m figwheel.main -O advanced -bo build
$ chmod +x bin/cache-clean.js
```

(Installing Clojure is simple - e.g. on a Mac it's `brew install clojure`)


## Thanks

Thanks to [Mage2 TV](https://www.mage2.tv/) for sponsoring the development of this tool.

This script was inspired by [Timon de Groot](https://twitter.com/TimonGreat)'s
[blog post](https://blog.timpack.org/speed-up-magento-development) where he
describes the idea to use a file watcher in PHPStorm to call `redis-cli` to
clear the complete cache whenever a XML file is modified.
The only downside of that solution is that it always flushes the full cache and
only works with redis.


## TODO

* The config cache should be cleaned for controllers once and only if they contain
a controller class declaration, i.e. they are picked up by Magento as a
controller for a route.

* Add support for the redis cache storage


## Copyright & License

Copyright 2018 by Vinai Kopp, distributed under the BSD-3-Clause license (see
the LICENSE file in this repository).
