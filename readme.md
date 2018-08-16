> "You know, hope is a mistake. If you can't fix what's broken, you'll, uh... you'll go insane." - Max Rockatansky

# Magento 2 Cache Clean

A faster drop in replacement for `bin/magento cache:clean` with a file watcher.

The file watcher automatically cleans affected cache types in the Magento 2
cache during development.
For example, if you make a change to a template, it only cleans the
`block_html` and `full_page` caches, not the `config` or `layout` caches.

The project is very young and only tested on MacOS and Linux.
Please report bugs by opening an issue on the GitHub issue tracker.


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
--help|-h               This help message
```

Usually I run the command once with the `--watch` switch when I start
development, and when I make a change that isn't automatically detected (yet),
I run `vendor/bin/cache-clean.js` with the given cache types as a drop in
replacement for `bin/magento cache:clean`.


### Prerequisites:

* `node.js` (built on 10.8, but should work with older versions, too).
* it probably is a good idea to turn on all Magento caches
  `bin/magento cache:enable` to get the full benefit.


## Rationale

This utility aims to improve the Magento developer experience by shortening the
feedback loop during development through automating the removal of affected
cache sections after file changes.

**Assumptions:**

1. Magento uses caching a lot and is faster when the caches are warm.
2. As a developer I want a quick feedback loop.
3. Rebuilding the cache is slower than cleaning the cache

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

* If you run into the error `Error NOSPC` on Linux, run the command:

``` shell
echo fs.inotify.max_user_watches=524288 | sudo tee -a /etc/sysctl.conf && sudo sysctl -p
```


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


## TODO

* Add Varnish support (bin/magento setup:config:set --http-cache-hosts=192.0.2.100,192.0.2.155:6081)
* Automatically clean relevant files in generated/code

## Copyright & License

Copyright 2018 by Vinai Kopp, distributed under the BSD-3-Clause license (see
the LICENSE file in this repository).
