(ns cache.cache
  (:require [cache.storage.redis :as redis]
            [cache.storage.file :as file]
            [cache.storage.varnish :as varnish]
            [cache.storage.remote-synchronized :as remote-synchronized]
            [cache.storage :as storage]
            [log.log :as log]
            [clojure.string :as string]
            [magento.app :as mage]
            [magento.fingerprint-file :as magefile]))

(defn- cachetype->tag [type]
  (or
    (get {"collections"                     "COLLECTION_DATA"
          "config_webservices"              "WEBSERVICE"
          "layout"                          "LAYOUT_GENERAL_CACHE_TAG"
          "full_page"                       "FPC"
          "config_integration_consolidated" "INTEGRATION_CONSOLIDATED"
          "config_integration_api"          "INTEGRATION_API_CONFIG"
          "config_integration"              "INTEGRATION"} type)
    (string/upper-case type)))

(defn- magefile->filetype [file]
  (reduce (fn [_ [filetype? type]]
            (when (filetype? file) (reduced type))) nil magefile/file->type))

(def filetype->cachetypes
  {::magefile/config      ["config"]
   ::magefile/translation ["translate" "full_page"]
   ::magefile/layout      ["layout" "full_page"]
   ::magefile/template    ["block_html" "full_page"]
   ::magefile/menu        ["config" "block_html"]
   ::magefile/fpc         ["full_page"]})

(defn magefile->cachetypes [file]
  (let [filetype (magefile->filetype file)]
    (get filetype->cachetypes filetype [])))

(defn magefile->cacheids [file]
  (magefile/file->ids file))

(defn get-storage
  "Note to self: decided against a multi-method because I'm not adding more
  backends, and a case statement is simpler."
  [config]
  (log/debug "Cache storage " config)
  (let [backend (:backend config)]
    (case backend
      ("Cm_Cache_Backend_Redis"
        "\\Magento\\Framework\\Cache\\Backend\\Redis"
        "Magento\\Framework\\Cache\\Backend\\Redis")
      (redis/create config)

      ("\\Magento\\Framework\\Cache\\Backend\\RemoteSynchronizedCache"
        "Magento\\Framework\\Cache\\Backend\\RemoteSynchronizedCache")
      (remote-synchronized/create (get-storage (remote-synchronized/extract-local-config config))
                                  (get-storage (remote-synchronized/extract-remote-config config)))

      (file/create config))))

(defn- clean
  ([cache] (storage/clean-all cache))
  ([cache type]
   (let [tag (cachetype->tag type)
         prefixed-tag (str (:id-prefix cache) tag)]
     (log/debug "Cleaning tag" tag)
     (storage/clean-tag cache prefixed-tag)))
  ([cache type & types]
   (run! #(clean cache %) (into [type] types))))

(defn- clean-full-page-cache
  "Clean both the full_page cache backend and varnish.
  If varnish is not configured or isn't responding it will be a null op.

  Knowing if varnish is enabled or not would require DB access
  which I want to avoid to keep things snappy."
  [base-dir]
  (log/debug "Using :page_cache cache backend")
  (let [cache (get-storage (mage/cache-config base-dir :page_cache))]
    (clean cache)
    (storage/close cache))
  (let [varnish (varnish/create (mage/varnish-hosts-config))]
    (storage/clean-all varnish)))

(defn- now []
  (.getTime (js/Date.)))

(def cache-clean-grace-period 500)                          ;; ms

(let [last-cleaned-map (atom {})]

  (defn- last-cleaned [cache-type-or-id]
    (get @last-cleaned-map cache-type-or-id))

  (defn- update-last-cleaned [cache-type-or-id]
    (swap! last-cleaned-map assoc cache-type-or-id (now))))

(defn- may-clean? [cache-type-or-id]
  (let [prev (last-cleaned cache-type-or-id)
        t (now)]
    (if (or (nil? prev) (< 0 (- t prev cache-clean-grace-period)))
      (do (update-last-cleaned cache-type-or-id)
          #_(log/notice "OK to clean " cache-type-or-id "since")
          true)
      (do #_(log/notice "WAIT grace period " cache-type-or-id (str (- t prev cache-clean-grace-period) "ms"))
          false))))

(defn- clean-cache-types-with-base-dir [base-dir cache-types]
  (when (or (empty? cache-types) (not= ["full_page"] cache-types))
    (log/debug "Using :default cache_backend")
    (let [cache (get-storage (mage/cache-config base-dir :default))
          cache-types (filter may-clean? (remove #(= "full_page" %) cache-types))]
      (apply clean cache cache-types)
      (storage/close cache)))

  (when (or (empty? cache-types) (some #{"full_page"} (filter may-clean? cache-types)))
    (clean-full-page-cache base-dir)))

(defn clean-cache-types [cache-types]
  (if (seq cache-types)
    (apply log/notice "Cleaning cache type(s)" cache-types)
    (log/notice "Flushing all caches"))
  (run! #(clean-cache-types-with-base-dir % cache-types) (mage/all-base-dirs)))

(defn- clean-cache-ids-with-base-dir [base-dir ids]
  (let [cache (get-storage (mage/cache-config base-dir :default))
        add-cache-id-prefix #(str (:id-prefix cache) %)]
    (->> ids
         (map string/upper-case)
         (map add-cache-id-prefix)
         (run! #(storage/clean-id cache %)))))

(defn clean-cache-ids [ids]
  (when (seq (filter may-clean? ids))
    (apply log/notice "Cleaning id(s):" ids)
    (run! #(clean-cache-ids-with-base-dir % ids) (mage/all-base-dirs))))
