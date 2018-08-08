(ns cache.filestorage
  (:require [file.system :as file]
            [cache.log :as log]
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
  (let [crypto (js/require
                "crypto")]
    (-> crypto (.createHash "md5") (.update data) (.digest "hex"))))

(defn- file-name-prefix []
  (:file-name-prefix options))

(defn- cache-id-prefix []
  (str (subs (md5 (str (file/realpath (base-dir)) "/app/etc/")) 0 3) "_"))

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

(defn id->filename [^String id]
  (str (file-name-prefix) "---" (cache-id-prefix) id))

(defn id->filepath [^String id]
  (str (path id) (id->filename id)))

(defn tag->filepath [^String tag]
  (str (tag-path) (id->filename tag)))

(defn- remove-cache-id-prefix [id-with-prefix]
  (subs id-with-prefix 4))

(defn tag->ids [tag]
  (let [file (tag->filepath tag)]
    (when (file/exists? file)
      (doall (map remove-cache-id-prefix (string/split-lines (file/slurp file)))))))

(defn clean-all []
  (log/debug "Cleaning dir" (cache-dir))
  (file/rm-contents (cache-dir)))

(defn delete [id]
  (log/debug "Cleaning id" id)
  (let [file (id->filepath id)]
    (when (file/exists? file)
      (file/rm file))))
