(ns magento.app
  (:require [file.system :as fs]
            [cache.storage :as storage]
            [log.log :as log]
            [cache.config :as config]
            [clojure.string :as string]
            [goog.json :as json]))

(defonce child-process (js/require "child_process"))

(defonce magento-basedir (atom "./"))

(defn set-base-dir! [dir]
  (reset! magento-basedir (fs/add-trailing-slash (string/replace dir "\\" "/"))))

(defn base-dir []
  (deref magento-basedir))

(defn app-config-dir []
  (str (base-dir) "app/etc/"))

(def memoized-app-config (memoize config/read-app-config))

(defn read-app-config []
  (memoized-app-config (base-dir)))

(def default-cache-id-prefix
  (memoize
   (fn []
     (let [path (fs/add-trailing-slash (fs/realpath (app-config-dir)))
           id-prefix (str (subs (storage/md5 path) 0 3) "_")]
       (log/debug "Calculated default cache ID prefix" id-prefix "from" path)
       id-prefix))))

(defn default-cache-dir [cache-type]
  (let [dir (if (= :page_cache cache-type) "page_cache" "cache")]
    (str (base-dir) "var/" dir)))

(defn read-cache-config []
  (let [config (read-app-config)]
    (get-in config [:cache :frontend])))

(defn file-cache-backend? [config]
  (or (not (:backend config))
      (= :backend "Cm_Cache_Backend_File")))

(defn missing-cache-dir? [config]
  (and (file-cache-backend? config) (not (:cache_dir config))))

(defn add-default-config-values [config cache-type]
  (cond-> config
    (file-cache-backend? config) (assoc :backend "Cm_Cache_Backend_File")
    (missing-cache-dir? config) (assoc :cache_dir (default-cache-dir cache-type))
    (not (:id_prefix config)) (assoc :id_prefix (default-cache-id-prefix))))

(defn cache-config
  "Given the cache type :default or :page_cache returns the configuration"
  [cache-type]
  (let [config (get (read-cache-config) cache-type {})]
    (add-default-config-values config cache-type)))

(defn varnish-hosts-config []
  (let [config (read-app-config)]
    (get config :http_cache_hosts)))

(defn module-dirs []
  (config/list-component-dirs (base-dir) "module"))

(defn theme-dirs []
  (config/list-component-dirs (base-dir) "theme"))
