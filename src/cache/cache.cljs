(ns cache.cache
  (:require [cache.storage.redis :as redis]
            [cache.storage.file :as file]
            [cache.storage :as storage]
            [log.log :as log]
            [clojure.string :as string]
            [magento.app :as mage]
            [magento.fingerprint-file :as magefile]))

(defn- cachetype->tag [type]
  (or
   (get {"collections" "COLLECTION_DATA"
         "config_webservices" "WEBSERVICE"
         "layout" "LAYOUT_GENERAL_CACHE_TAG"
         "full_page" "FPC"
         "config_integration_consolidated" "INTEGRATION_CONSOLIDATED"
         "config_integration_api" "INTEGRATION_API_CONFIG"
         "config_integration" "INTEGRATION"} type)
   (string/upper-case type)))

(defn- magefile->filetype [file]
  (reduce (fn [_ [filetype? type]]
            (when (filetype? file) (reduced type))) nil magefile/file->type))

(def filetype->cachetypes
  {::magefile/config ["config"]
   ::magefile/translation ["translate"]
   ::magefile/layout ["layout" "full_page"]
   ::magefile/template ["block_html" "full_page"]
   ::magefile/requirejs-config ["full_page"]})

(defn magefile->cachetypes [file]
  (let [filetype (magefile->filetype file)]
    (get filetype->cachetypes filetype [])))

(defn- clean
  ([cache] (storage/clean-all cache))
  ([cache type]
   (let [tag (cachetype->tag type)]
     (log/debug "Cleaning tag" tag)
     (storage/clean-tag cache tag)))
  ([cache type & types]
   (run! #(clean cache %) (into [type] types))))

(def get-storage
  "TODO: memoize this once it is more stable
  Note to self: decided against a multi-method because I'm not adding more'
  backends, and an if statement is simpler."
  (fn [config]
    (log/debug "Cache storage " config)
    (if (= "Cm_Cache_Backend_Redis" (:backend config))
      (redis/create config)
      (file/create config))))

(defn tag->ids [cache tag]
  (storage/tag->ids cache tag))

(defn clean-cache-types [cache-types]
  (if (seq cache-types)
    (apply log/notice "Cleaning cache type(s)" cache-types)
    (log/notice "Flushing all caches"))

  (when (or (empty? cache-types) (not= ["full_page"] cache-types))
    (log/debug "Using :default cache_backend")
    (let [cache (get-storage (mage/cache-config :default))
          cache-types (remove #(= "full_page" %) cache-types)]
      (apply clean cache cache-types)))

  (when (or (empty? cache-types) (some #{"full_page"} cache-types))
    (log/debug "Using :page_cache cache backend")
    (let [cache (get-storage (mage/cache-config :page_cache))]
      (clean cache))))
