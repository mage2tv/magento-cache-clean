## Bug in Full Page Cache Filesystem Storage Config

Magento 2.1 introduced a bug that causes the full page cache records to be written to the `var/cache` directory instead of `var/page_cache`.
The issue is fixed in 2.3.4 (not yet released at the time of writing).

The bug is only "active" if the default Magento cache configuration is used.

The issue the bug causes is that the cache cleaner sometimes seem to not work (e.g. the full page cache is not cleaned, even though it says it is).

The cache-clean.js utility contains a workaround since release 1.0.22. If it finds an instance where the bug is active, it will use the `var/cache` directory for cleaning the full page cache.

However, this will lead to much higher load times, because as a side effect, every time the full page cache is cleaned, the config cache will be cleaned, too!

The solution is simple: removing all cache configuration from the default `app/etc/env.php` will fix the issue.

In short, replace this:
```
    'cache' => [
        'frontend' => [
            'default' => [
                'id_prefix' => 'e74_'
            ],
            'page_cache' => [
                'id_prefix' => 'e74_'
            ]
        ]
    ],
```

with this:
```
```

Another alternative solution is to add the full page cache directory to the configuration like this:

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

Or use Redis or Varnish as the full page cache storage, since the bug only affects the default config with the file system cache backend.
Redis or Varnish are not affected.

The bug will be fixed in the (currently upcoming) Magento 2.3.4 release.

Here is the related PR:
Reference https://github.com/magento/magento2/pull/22228
