(ns cache.storage.redis
  (:require [cache.storage]))

(defrecord Redis [config]
  cache.storage/CacheStorage

  (tag->ids [this tag]
    [])

  (delete [this id]
    )

  (clean-tag [this tag]
    )

  (clean-all [this]
    ))

(defn create [config]
  (->Redis config))
