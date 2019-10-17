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

Automating selective cache cleaning improves the developer experience and enables
faster delivery of results.
