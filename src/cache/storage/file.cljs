(ns cache.storage.file
  (:require [file.system :as fs]
            [cache.storage :as storage]
            [log.log :as log]
            [clojure.string :as string]))

(def options
  {:file-name-prefix       "mage"
   :hashed-directory-level 1})

(defn- file-name-prefix []
  (:file-name-prefix options))

(defn- chars-from-end [^String s length]
  (subs s (- (count s) length)))

(defn- path [^String cache-dir ^String id]
  (let [length (:hashed-directory-level options)]
    (if (< 0 length)
      (let [suffix (chars-from-end (storage/md5 id) length)]
        (str cache-dir (file-name-prefix) "--" suffix "/"))
      cache-dir)))

(defn- tag-path [cache-dir]
  (str cache-dir (file-name-prefix) "-tags/"))

(defn- id->filename [^String id]
  (str (file-name-prefix) "---" id))

(defn- id->filepath [^String cache-dir ^String id]
  (str (path cache-dir id) (id->filename id)))

(defn- tag->filepath [^String cache-dir ^String tag]
  (str (tag-path cache-dir) (id->filename tag)))

(defn- tag->ids [cache-dir tag]
  (let [file (tag->filepath cache-dir tag)]
    (if (fs/exists? file)
      (string/split-lines (fs/slurp file))
      [])))

(defn- delete [cache-dir id]
  (let [file (id->filepath cache-dir id)]
    (log/debug "cleaning file" file)
    (when (fs/exists? file)
      (fs/rm file))))

(defrecord File [cache-dir id-prefix]
  storage/CacheStorage

  (clean-tag [this tag]
    (let [tag-file (tag->filepath cache-dir tag)]
      (log/debug "Tag-file" tag-file)
      (when (fs/exists? tag-file)
        (run! #(delete cache-dir %) (tag->ids cache-dir tag))
        (fs/rm tag-file))))

  (clean-id [this id]
    (delete cache-dir id))

  (clean-all [this]
    (log/debug "Cleaning dir" cache-dir)
    (when (fs/exists? cache-dir)
      (fs/rm-contents cache-dir)))

  (close [this]))

(defn create [config]
  (map->File {:cache-dir (fs/add-trailing-slash (:cache_dir config))
              :id-prefix (:id_prefix config)}))
