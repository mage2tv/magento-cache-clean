(ns cache.storage
  (:require [magento.app :as mage]
            [file.system :as fs]))

(defn md5 [^String data]
  (let [crypto (js/require "crypto")]
    (-> crypto (.createHash "md5") (.update data) (.digest "hex"))))

(defn magento-instance-cache-id-prefix []
  (str (subs (md5 (str (fs/realpath (mage/base-dir)) "/app/etc/")) 0 3) "_"))

(defprotocol CacheStorage
  (clean-tag [storage tag] "Deletes all cache records associated with the given tag.")
  (clean-all [storage] "Delete all cache records and tags."))
