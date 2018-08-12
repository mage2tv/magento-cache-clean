(ns cache.storage.file
  (:require [file.system :as fs]
            [magento.app :as mage]
            [cache.storage :as storage]
            [log.log :as log]
            [clojure.string :as string]))

(def options
  {:file-name-prefix       "mage"
   :hashed-directory-level 1})

(defn- md5 [^String data]
  (let [crypto (js/require "crypto")]
    (-> crypto (.createHash "md5") (.update data) (.digest "hex"))))

(defn- file-name-prefix []
  (:file-name-prefix options))

(defn- cache-id-prefix []
  (str (subs (md5 (str (fs/realpath (mage/base-dir)) "/app/etc/")) 0 3) "_"))

(defn- chars-from-end [^String s length]
  (subs s (- (count s) length)))

(defn- path [^String cache-dir ^String id]
  (let [length (:hashed-directory-level options)]
    (if (< 0 length)
      (let [suffix (chars-from-end (md5 (str (cache-id-prefix) id)) length)]
        (str cache-dir (file-name-prefix) "--" suffix "/"))
      cache-dir)))

(defn- tag-path [cache-dir]
  (str cache-dir (file-name-prefix) "-tags/"))

(defn- id->filename [^String id]
  (str (file-name-prefix) "---" (cache-id-prefix) id))

(defn- id->filepath [^String cache-dir ^String id]
  (str (path cache-dir id) (id->filename id)))

(defn- tag->filepath [^String cache-dir ^String tag]
  (str (tag-path cache-dir) (id->filename tag)))

(defn- remove-cache-id-prefix [id-with-prefix]
  (subs id-with-prefix 4))

(defrecord File [cache-dir]
  storage/CacheStorage

  (tag->ids [this tag]
    (let [file (tag->filepath cache-dir tag)]
      (if (fs/exists? file)
        (doall (map remove-cache-id-prefix (string/split-lines (fs/slurp file))))
        [])))

  (delete [this id]
    (log/debug "Cleaning id" id)
    (let [file (id->filepath cache-dir id)]
      (when (fs/exists? file)
        (fs/rm file))))

  (clean-tag [this tag]
    (let [tag-file (tag->filepath cache-dir tag)]
      (log/debug "Tag-file" tag-file)
      (when (fs/exists? tag-file)
        (run! #(storage/delete this %) (storage/tag->ids this tag))
        (fs/rm tag-file))))

  (clean-all [this]
    (log/debug "Cleaning dir" cache-dir)
    (when (fs/exists? cache-dir)
      (fs/rm-contents cache-dir))))

(defn create [config]
  (map->File {:cache-dir (fs/add-trailing-slash (:cache_dir config))}))
