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

(defn app-config-dir [base-dir]
  (str base-dir "app/etc/"))

(defn integration-test-base-dirs [base-dir]
  (let [tests-tmp-base-dir (str base-dir "dev/tests/integration/tmp")]
    (when (fs/dir? tests-tmp-base-dir)
      (->> (fs/ls tests-tmp-base-dir)
           (filter #(string/starts-with? (fs/basename %) "sandbox-"))
           (filter #(fs/file? (str % "/etc/env.php")))
           (map fs/add-trailing-slash)))))

(defn all-base-dirs []
  (let [base-dir (base-dir)]
    (into [base-dir] (integration-test-base-dirs base-dir))))

(def memoized-app-config (memoize config/read-app-config))

(defn read-app-config [base-dir]
  (memoized-app-config base-dir))

(def default-cache-id-prefix
  (memoize
   (fn [base-dir]
     (let [path (fs/add-trailing-slash (fs/realpath (app-config-dir base-dir)))
           id-prefix (str (subs (storage/md5 path) 0 3) "_")]
       (log/debug "Calculated default cache ID prefix" id-prefix "from" path)
       id-prefix))))

(defn default-cache-dir [base-dir cache-type]
  (let [dir (if (= :page_cache cache-type) "page_cache" "cache")]
    (str base-dir "var/" dir)))

(defn read-cache-config [base-dir]
  (let [config (read-app-config base-dir)]
    (get-in config [:cache :frontend])))

(defn file-cache-backend? [config]
  (or (not (:backend config))
      (= :backend "Cm_Cache_Backend_File")))

(defn missing-cache-dir? [config]
  (and (file-cache-backend? config) (not (:cache_dir config))))

(defn add-default-config-values [base-dir config cache-type]
  (cond-> config
    (file-cache-backend? config) (assoc :backend "Cm_Cache_Backend_File")
    (missing-cache-dir? config) (assoc :cache_dir (default-cache-dir base-dir cache-type))
    (not (:id_prefix config)) (assoc :id_prefix (default-cache-id-prefix base-dir))))

(defn cache-config
  "Given the cache type :default or :page_cache returns the configuration"
  [base-dir cache-type]
  (let [config (get (read-cache-config base-dir) cache-type {})]
    (add-default-config-values base-dir config cache-type)))

(defn varnish-hosts-config []
  (let [config (read-app-config (base-dir))]
    (get config :http_cache_hosts)))

(defn module-dirs []
  (config/list-component-dirs (base-dir) "module"))

(defn theme-dirs []
  (config/list-component-dirs (base-dir) "theme"))
