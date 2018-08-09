# Magento Cache Cleaner

Small filewatcher to automate selectively cleaning affected cache segments in
the Magento 2 file cache backend during development.

## Rationale

## Usage

## Building

To build run

```shell
$ clj -m figwheel.main -O advanced -bo build
$ chmod +x bin/clean-cache.js
```

## TODO

Add composer.json

The config cache should be cleaned for controllers once and only if they contain
a controller class declaration, i.e. they are picked up by Magento as a
controller for a route.
