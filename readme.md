# Magento 2 Cache Clean

Since release 1.1.0, this package has moved to `mage-os/magento-cache-clean`.  

For backward compatibility purposes, this repo still exists and can be installed, but it is only an empty wrapper requiring the latest version of the new package.  
Please switch to use `mage-os/magento-cache-clean` at your convenience, as this package may be removed at a future date.

Since the package name is not included in the binary path `vendor/bin/cache-clean.js` everything should continue to work as before.

Please open PRs or issues at [github.com/mage-os/magento-cache-clean](https://github.com/mage-os/magento-cache-clean)

## Switching a project installation

```sh
composer remove --dev mage2tv/magento-cache-clean
composer require --dev mage-os/magento-cache-clean
```

## Swwitching a global installation

```sh
composer global remove mage2tv/magento-cache-clean
composer global require mage-os/magento-cache-clean
```

