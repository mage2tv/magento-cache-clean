(ns cache.storage
  (:require [file.system :as fs]))

(defn md5 [^String data]
  (let [crypto (js/require "crypto")]
    (-> crypto (.createHash "md5") (.update data) (.digest "hex"))))

(defprotocol CacheStorage
  (clean-tag [storage tag] "Deletes all cache records associated with the given tag.")
  (clean-id [storage id] "Delete the cache record matching the given ID")
  (clean-all [storage] "Delete all cache records and tags.")
  (close [storage] "Close connection to storage, called before shutdown."))
