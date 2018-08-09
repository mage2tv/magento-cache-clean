# Magento Cache Cleaner

# Work In Progress!

Small filewatcher to automate selectively cleaning affected cache segments in
the Magento 2 file cache backend during development.

### Building

To build run `clj -m figwheel.main -O advanced -bo build`

### TODO

Add composer.json

The config cache should be cleaned for controllers once and only if they contain
a controller class declaration, i.e. they are picked up by Magento as a
controller for a route.

Make magento.watcher/stop not timeout sometimes
