(ns cache.storage.file
  (:require [file.system :as fs]
            [cache.storage :as storage]
            [log.log :as log]
            [clojure.string :as string]
            [util.crypto :refer [md5]]))

(def options {:hashed-directory-level 1})

(defn- chars-from-end [^String s length]
  (subs s (- (count s) length)))

(defn- path [^String cache-dir ^String file-name-prefix ^String id]
  (let [length (:hashed-directory-level options)]
    (if (< 0 length)
      (let [suffix (chars-from-end (md5 id) length)]
        (str cache-dir file-name-prefix "--" suffix "/"))
      cache-dir)))

(defn- tag-path [cache-dir file-name-prefix]
  (str cache-dir file-name-prefix "-tags/"))

(defn- id->filename [^String file-name-prefix ^String id]
  (str file-name-prefix "---" id))

(defn- id->filepath [^String cache-dir ^String file-name-prefix ^String id]
  (str (path cache-dir file-name-prefix id) (id->filename file-name-prefix id)))

(defn- tag->filepath [^String cache-dir ^String file-name-prefix ^String tag]
  (str (tag-path cache-dir file-name-prefix) (id->filename file-name-prefix tag)))

(defn- tag->ids [cache-dir file-name-prefix tag]
  (let [file (tag->filepath cache-dir file-name-prefix tag)]
    (if (fs/exists? file)
      (string/split-lines (fs/slurp file))
      [])))

(defn- delete [cache-dir file-name-prefix id]
  (let [file (id->filepath cache-dir file-name-prefix id)]
    (log/debug "cleaning file" file)
    (try (fs/rm file)
         (catch :default e
           (log/debug :without-time "Error deleting file:" (str e))))))

(defrecord File [cache-dir id-prefix file-name-prefix]
  storage/CacheStorage

  (clean-tag [this tag]
    (let [tag-file (tag->filepath cache-dir file-name-prefix tag)]
      (log/debug "Tag-file" tag-file)
      (when (fs/exists? tag-file)
        (run! #(delete cache-dir file-name-prefix %) (tag->ids cache-dir file-name-prefix tag))
        (fs/rm tag-file))))

  (clean-id [this id]
    (delete cache-dir file-name-prefix id))

  (clean-all [this]
    (log/debug "Cleaning dir" cache-dir)
    (when (fs/exists? cache-dir)
      (fs/rm-contents cache-dir)))

  (close [this]))

(defn create [config]
  (let [cache-dir (-> config :backend_options :cache_dir)]
    (when-not cache-dir
      (throw (ex-info (str "No cache_dir property present in file cache backend_options.") config)))
    (map->File {:cache-dir (fs/add-trailing-slash cache-dir)
                :id-prefix (:id_prefix config)
                :file-name-prefix (or (-> config :backend_options :file_name_prefix)
                                      "mage")})))
