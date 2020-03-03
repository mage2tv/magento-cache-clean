## Using the watcher from code instead of the CLI

*This feature is currently experimental.*

The API should be considered *alpha* stability.

### Quickstart

```js
require('vendor/mage2tv/magento-cache-clean/export/watcher');
cache.watcher.set_base_dir('.');
cache.watcher.set_log_level(1);
cache.watcher.watch();
```

### Howto

First, the file with the exported functions needs to be included.
This is not a native node application, so the exports are global functions on the `cache.watcher` namespace.

Assuming the package is installed as a Magento development dependency, and the current working directory is that dir, the functions can be imported into the global namespace via:

```js
require('vendor/mage2tv/magento-cache-clean/export/watcher');
```

Then the Magento base directory needs to be set

```js
cache.watcher.set_base_dir('/path/to/magento');
```

By default the log level is `0`, which means only errors are output.
If you want a different log level, it needs to be set:

```js
// Log Levels: {debug: 3, info: 2, notice: 1, error: 0}
cache.watcher.set_log_level(1);
```

Now that the Magento base directory and the log level are set, the watcher can be started:

```js
cache.watcher.watch();
```


**Please note that the API still might change in a backward incompatible manner based on user feedback.**
