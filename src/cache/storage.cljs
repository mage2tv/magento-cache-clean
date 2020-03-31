(ns cache.storage)

(defprotocol CacheStorage
  (clean-tag [storage tag] "Deletes all cache records associated with the given tag.")
  (clean-id [storage id] "Delete the cache record matching the given ID")
  (clean-all [storage] "Delete all cache records and tags.")
  (close [storage] "Close connection to storage, called before shutdown."))
