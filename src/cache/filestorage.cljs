(ns cache.filestorage
  (:require [file.system :as fs]
            [log.log :as log]
            [clojure.string :as string]))

(def options
  {:file-name-prefix       "mage"
   :hashed-directory-level 1})

(def magento-basedir (atom "./"))

(def ^:dynamic *cachedir* "var/cache/")

(def add-trailing-slash
  (memoize
   (fn [dir]
     (if (= \/ (last dir))
       dir
       (str dir "/")))))

(defn set-magento-dir! [dir]
  (reset! magento-basedir dir))

(defn base-dir []
  (add-trailing-slash @magento-basedir))

(defn- cache-dir []
  (add-trailing-slash (str (base-dir) *cachedir*)))

(defn- md5 [^String data]
  (let [crypto (js/require "crypto")]
    (-> crypto (.createHash "md5") (.update data) (.digest "hex"))))

(defn- file-name-prefix []
  (:file-name-prefix options))

(defn- cache-id-prefix []
  (str (subs (md5 (str (fs/realpath (base-dir)) "/app/etc/")) 0 3) "_"))

(defn- chars-from-end [^String s length]
  (subs s (- (count s) length)))

(defn- path [^String id]
  (let [length (:hashed-directory-level options)
        dir    (cache-dir)]
    (if (< 0 length)
      (str dir (file-name-prefix) "--" (chars-from-end (md5 (str (cache-id-prefix) id)) length) "/")
      dir)))

(defn- tag-path []
  (str (cache-dir) (file-name-prefix) "-tags/"))

(defn- id->filename [^String id]
  (str (file-name-prefix) "---" (cache-id-prefix) id))

(defn- id->filepath [^String id]
  (str (path id) (id->filename id)))

(defn- tag->filepath [^String tag]
  (str (tag-path) (id->filename tag)))

(defn- remove-cache-id-prefix [id-with-prefix]
  (subs id-with-prefix 4))

(defn tag->ids [tag]
  (let [file (tag->filepath tag)]
    (if (fs/exists? file)
      (doall (map remove-cache-id-prefix (string/split-lines (fs/slurp file))))
      [])))

(defn delete [id]
  (log/debug "Cleaning id" id)
  (let [file (id->filepath id)]
    (when (fs/exists? file)
      (fs/rm file))))

(defn clean-tag [tag]
  (let [file (tag->filepath tag)]
    (when (fs/exists? file)
      (run! delete (tag->ids tag))
      (fs/rm file))))

(defn clean-all []
  (log/debug "Cleaning dir" (cache-dir))
  (let [dir (cache-dir)]
    (when (fs/exists? dir)
      (fs/rm-contents dir))))
