## Starting the watcher from code instead of the CLI

The API should be considered *beta* stability. It might still change in future in a backward incompatible manner.


### Quickstart

```js
require('./vendor/mage2tv/magento-cache-clean/export/watcher');

const mageBaseDir = './';
const logLevel = 2; // Log Levels: {debug: 3, info: 2, notice: 1, error: 0}

cache.watcher.watch(mageBaseDir, logLevel);
```


### More detailed instructions

First, the file with the exported functions needs to be included.

Assuming the package is installed as a Magento development composer dependency, and the current working directory is that Magento base dir, the functions can be imported into the global namespace via:

```js
require('./vendor/mage2tv/magento-cache-clean/export/watcher');
```

Since the watcher is not a native node application, the exports are global functions on the `cache.watcher` pseudo "namespace".


#### Base Directory and Log Level

All exported functions take the Magento base directory path as the first argument.
The path can be relative to the current working directory, or it can be an absolute path.

The second argument to all exported functions is the log level.  
The log level is specified as an integer as follows:

| Name | Level |
| -----|-------|
| Error | 0 |
| Notice | 1 |
| Info | 2 |
| Debug | 3 |

The higher the number, the more verbose will the output be.

(The default log level when the watcher is used from the command line is `1`).


#### Starting the File Watcher


After loading the code, the watcher can be started with the `watch` function:

```js
cache.watcher.watch('/path/to/magento', logLevel);
```


#### Cleaning Cache by Tags or Types

Cache tags or types can be cleaned with the `cleanTags` function.
If a type is specified - e.g. `full_page`, it will automatically be translated into the matching cache tags (e.g. `FPC`).
If a string is not recognized as a cache type, it will be used as a cache tag.


```js
cache.watcher.cleanTags('/path/to/magento', logLevel, ...tags);
```

The cache types in a native 2.3.4 Magento Open Source installation are:

* `config`
* `layout`
* `block_html`
* `collections`
* `reflection`
* `db_ddl`
* `compiled_config`
* `eav`
* `customer_notification`
* `config_integration`
* `google_product`
* `full_page`
* `config_webservice`
* `translate`

You can get the list for your instance with the command `bin/magento cache:status`

##### Example

The Layout, Block and Full Page Cache can be cleaned like this:

```js
const types = ['layout', 'block_html', 'full_page'];
cache.watcher.cleanTags(baseDir, logLevel, ...types);
```


#### Cleaning Cache by Cache IDs

Individual cache records can be cleaned by specifying the cache IDs as arguments to the `cleanIds` function. 

```js
cache.watcher.cleanIds('/path/to/magento', logLevel, ...ids);
```

##### Example

```js
const ids = ['global__event_config_cache', 'webapi_config'];
cache.watcher.cleanTags(baseDir, logLevel, ...ids);
```


#### Cleaning All Caches

The complete cache storage can be cleaned with the `cleanAll` function.

```js
cache.watcher.cleanAll('/path/to/magento', logLevel);
```


**Please note that the API still might change in a backward incompatible manner based on user feedback.**
