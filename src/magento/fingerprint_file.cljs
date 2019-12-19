(ns magento.fingerprint-file
  (:require [file.system :as fs]
            [clojure.string :as string]))

(defn windowsify [path]
  (string/replace path "/" "\\\\"))

(defn path-pattern [path-pattern]
  (re-pattern (cond-> path-pattern
                (fs/win?) windowsify)))

(defn- match-name? [path-pattern file]
  (and (string? file) (re-find path-pattern file)))

(defn- file-fingerprint-fn [name-pattern]
  (fn [file]
    (match-name? name-pattern file)))

(defn- filenames->fingerprint-fns [type filenames]
  (reduce (fn [acc filename]
            (assoc acc (file-fingerprint-fn (path-pattern filename)) type)) {} filenames))

(defn- config-filetypes []
  (let [res ["/etc(?:/[^/]+|)/di\\.xml$"
             "/etc/widget\\.xml$"
             "/etc/product_types\\.xml$"
             "/etc/product_options\\.xml$"
             "/etc/payment\\.xml$"
             "/etc/search_request\\.xml$"
             "/etc/config\\.xml$"
             "/etc/indexer\\.xml$"]]
    (filenames->fingerprint-fns ::config res)))

(defn- layout-filetypes []
  (let [res ["/layout/.+\\.xml$"
             "/page_layout/.+\\.xml$"]]
    (filenames->fingerprint-fns ::layout res)))

(defn- translation-filetypes []
  (let [res ["/i18n/.+\\.csv$"]]
    (filenames->fingerprint-fns ::translation res)))

(defn- template-filetypes []
  (let [res ["/templates/.+\\.phtml$"
             "/templates/.+\\.twig$"
             "/etc/view\\.xml$"
             "/theme\\.xml$"]]
    (filenames->fingerprint-fns ::template res)))

(defn- menu-filetypes []
  (let [res ["/etc/adminhtml/menu\\.xml$"]]
    (filenames->fingerprint-fns ::menu res)))

(defn- full-page-cache-only-filetypes []
  (let [res ["/etc/frontend/sections\\.xml$" ;; section names list in head
             "/view/(?:base|frontend|adminhtml)/requirejs-config\\.js$"]]
    (filenames->fingerprint-fns ::fpc res)))

(def file->type
  (merge (config-filetypes)
         (layout-filetypes)
         (translation-filetypes)
         (template-filetypes)
         (menu-filetypes)
         (full-page-cache-only-filetypes)))

(defn- make-ui-component->ids-fn
  "Return a matcher fn where the returned cache id contains part of the file name."[]
  (let [ui-comp-pattern (path-pattern "/ui_component/(.+)\\.xml$")]
    (fn [file]
      (when-let [m (re-find ui-comp-pattern file)]
        [(str "ui_component_configuration_data_" (second m))]))))

(defn- make-file->ids
  "Configure the mapping of file name regex to cache-id's to be cleaned on
  changes. It would also be possible to clear the complete config cache type
  for these, but this more granular approach should allow for faster cache
  rebuild times. If there are problems add them to config-filetypes above."
  []
  (let [res [["/etc/adminhtml/system\\.xml$" ["adminhtml__backend_system_configuration_structure"]]
             ["/etc/queue\\.xml$" ["message_queue_config_cache"]]
             ["/etc/queue_consumer\\.xml$" ["message_queue_consumer_config_cache"]]
             ["/etc/queue_publisher\\.xml$" ["message_queue_publisher_config_cache"]]
             ["/etc/queue_topology\\.xml$" ["message_queue_topology_config_cache"]]
             ["/etc/crontab\\.xml$" ["crontab_config_cache"]]
             ["/etc/events\\.xml" ["global__event_config_cache"]]
             ["/etc/frontend/events\\.xml$" ["frontend__event_config_cache"]]
             ["/etc/adminhtml/events\\.xml$" ["adminhtml__event_config_cache"]]
             ["/etc/webapi_rest/events\\.xml$" ["webapi_rest__event_config_cache"]]
             ["/etc/webapi_soap/events\\.xml$" ["webapi_soap__event_config_cache"]]
             ["/etc/crontab/events\\.xml$" ["crontab__event_config_cache"]]
             ["/etc/frontend/sections\\.xml$" ["sections_invalidation_config"]]
             ["/etc/email_templates\\.xml$" ["email_templates"]]
             ["/etc/webapi\\.xml" ["webapi_config"]]
             ["/etc/schema.graphqls" ["magento_framework_graphqlschemastitching_config_data"]]
             ["/etc/catalog_attributes\\.xml" ["catalog_attributes"]]
             ["/etc/sales\\.xml" ["sales_totals_config_cache"]]
             ["/etc/extension_attributes\\.xml" ["extension_attributes_config"]]
             ["/etc/acl\\.xml$" ["provider_acl_resources_cache"]]
             ["/etc/frontend/routes\\.xml$" ["frontend::RoutesConfig"]]
             ["/etc/adminhtml/routes\\.xml$" ["adminhtml::RoutesConfig"]]]
        id-fns (map (fn [[pattern-string ids]]
                      (let [re (path-pattern pattern-string)]
                        (fn [file]
                          #_(prn pattern-string)
                          (when (match-name? re file) ids)))) res)]
    (apply some-fn (conj id-fns (make-ui-component->ids-fn)))))

(def file->ids (make-file->ids))
