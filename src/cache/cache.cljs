(ns cache.cache
  (:require [cache.filestorage :as storage]
            [file.system :as file]
            [cache.log :as log]
            [clojure.string :as string]))

(defn cachetype->tag [type]
  (or
   (get {"collections" "COLLECTION_DATA"
         "config_webservices" "WEBSERVICE"
         "layout" "LAYOUT_GENERAL_CACHE_TAG"
         "full_page" "FPC"
         "config_integration_consolidated" "INTEGRATION_CONSOLIDATED"
         "config_integration_api" "INTEGRATION_API_CONFIG"
         "config_integration" "INTEGRATION"} type)
   (string/upper-case type)))

(defn- match-name? [name-pattern file]
  (cond
    (regexp? name-pattern) (re-find name-pattern file)
    :else (= name-pattern (file/basename file))))

(defn- file-fingerprint-fn [name-pattern content-head-pattern]
  (fn [file]
    (and (match-name? name-pattern file)
         (file/exists? file)
         (or (nil? content-head-pattern)
             (re-find content-head-pattern (file/head file))))))

(defn- tuples->fingerprint-fns [type tuples]
  (reduce (fn [acc [filename content]]
            (assoc acc (file-fingerprint-fn filename content) type)) {} tuples))

(defn- config-filetypes []
  (let [t [["di.xml" #"urn:magento:framework:ObjectManager/etc/config\.xsd"]
           ["crontab.xml" #"urn:magento:module:Magento_Cron:etc/crontab\.xsd"]
           ["events.xml" #"urn:magento:framework:Event/etc/events\.xsd"]
           ["extension_attributes.xml" #"urn:magento:framework:Api/etc/extension_attributes\.xsd"]
           ["routes.xml" #"urn:magento:framework:App/etc/routes\.xsd"]
           ["widget.xml" #"urn:magento:module:Magento_Widget:etc/widget\.xsd"]
           ["product_types.xml" #"urn:magento:module:Magento_Catalog:etc/product_types\.xsd"]
           ["product_options.xml" #"urn:magento:module:Magento_Catalog:etc/product_options\.xsd"]
           ["payment.xml" #"urn:magento:module:Magento_Payment:etc/payment\.xsd"]
           ["search_request.xml" #"urn:magento:framework:Search/etc/search_request\.xsd"]
           ["config.xml" #"urn:magento:module:Magento_Store:etc/config\.xsd"]
           [#"/ui_component/.+\.xml$" #"urn:magento:module:Magento_Ui:etc/ui_configuration\.xsd"]
           ["menu.xml" #"urn:magento:module:Magento_Backend:etc/menu\.xsd"]
           ["acl.xml" #"urn:magento:framework:Acl/etc/acl\.xsd"]
           ["indexer.xml" #"urn:magento:framework:Indexer/etc/indexer\.xsd"]]]
    (tuples->fingerprint-fns ::config t)))

(defn- layout-filetypes []
  (let [t [[#"/layout/.+\.xml$" #"<page [^>]+\"urn:magento:framework:View/Layout/etc/page_configuration\.xsd\""]]]
    (tuples->fingerprint-fns ::layout t)))

(defn- translation-filetypes []
  (let [t [[#"/i18n/.+\.csv$" #".+,.+"]]]
    (tuples->fingerprint-fns ::translation t)))

(defn- template-filetypes []
  (let [t [[#"/templates/.+\.phtml"]]]
    (tuples->fingerprint-fns ::template t)))

(def file->type
  (merge (config-filetypes)
         (layout-filetypes)
         (translation-filetypes)
         (template-filetypes)))

(defn- magefile->filetype [file]
  (reduce (fn [_ [filetype? type]]
            (when (filetype? file) (reduced type))) nil file->type))

(defn tag->ids [tag]
  (if (file/exists? (storage/tag->filepath tag))
    (storage/tag->ids tag)
    []))

(defn id->file [id]
  (storage/id->filepath id))

(def filetype->cachetypes
  {::config ["config"]
   ::translation ["translate"]
   ::layout ["layout" "full_page"]
   ::template ["block_html" "full_page"]})

(defn magefile->cachetypes [file]
  (let [filetype (magefile->filetype file)]
    (get filetype->cachetypes filetype [])))

(defn- rm-tagfile [tag]
  (let [file (storage/tag->filepath tag)]
    (when (file/exists? file)
      (file/rm file))))

(defn- clean
  ([] (storage/clean-all))
  ([type]
   (let [tag (cachetype->tag type)]
     (log/debug "Cleaning tag" tag)
     (run! storage/delete (tag->ids tag))
     (rm-tagfile tag)))
  ([type & types]
   (run! #(clean %) (into [type] types))))

(defn clean-cache-types [cache-types]
  (apply log/notice "Cleaning cache type(s)" cache-types)
  (when (or (empty? cache-types) (not= ["full_page"] cache-types))
    (log/debug "Using cache dir var/cache...")
    (binding [storage/*cachedir* "var/cache/"]
      (let [cache-types (remove #(= "full_page" %) cache-types)]
        (apply clean cache-types))))
  (when (or (empty? cache-types) (some #{"full_page"} cache-types))
    (log/debug "Using cache dir var/page_cache...")
    (binding [storage/*cachedir* "var/page_cache/"]
      (clean))))
