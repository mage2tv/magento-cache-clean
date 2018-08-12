(ns cache.storage.redis
  (:require [cache.storage :as storage]
            [log.log :as log]))

(defrecord Redis [config]
  storage/CacheStorage

  (tag->ids [this tag]
    [])

  (delete [this id]
    )

  (clean-tag [this tag]
    )

  (clean-all [this]
    ))

(defn create [config]
  (log/error "[Error] REDIS cache storage not yet supported.")
  (->Redis config))
