(ns cache.storage)

(defprotocol CacheStorage
  (tag->ids [storage tag] "Takes a cache tag and returns a seq of ids associated with the tag.")
  (delete [storage id] "Deletes the cache record with the given ID")
  (clean-tag [storage tag] "Deletes all cache records associated with the given tag.")
  (clean-all [storage] "Delete all cache records and tags."))
