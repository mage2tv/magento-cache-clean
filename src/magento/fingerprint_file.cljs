(ns magento.fingerprint-file
  (:require [file.system :as fs]
            [clojure.string :as string]))

(defn- match-name? [name-pattern file]
  (cond
    (regexp? name-pattern) (re-find name-pattern file)
    :else (= name-pattern (fs/basename file))))

(defn- file-fingerprint-fn [name-pattern content-head-pattern]
  (fn [file]
    (and (match-name? name-pattern file)
         (fs/exists? file)
         (or (nil? content-head-pattern)
             (re-find content-head-pattern (fs/head file))))))

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
  (let [t [[#"/layout/.+\.xml$"]
           [#"/page_layout/.+\.xml$"]]]
    (tuples->fingerprint-fns ::layout t)))

(defn- translation-filetypes []
  (let [t [[#"/i18n/.+\.csv$" #".+,.+"]]]
    (tuples->fingerprint-fns ::translation t)))

(defn- template-filetypes []
  (let [t [[#"/templates/.+\.phtml"]
           [#"/etc/view.xml" #"urn:magento:framework:Config/etc/view\.xsd"]
           ["theme.xml" #"urn:magento:framework:Config/etc/theme\.xsd"]]]
    (tuples->fingerprint-fns ::template t)))

(defn- requirejs-config-filetypes []
  (let [t [["requirejs-config.js"]]]
    (tuples->fingerprint-fns ::requirejs-config t)))

(def file->type
  (merge (config-filetypes)
         (layout-filetypes)
         (translation-filetypes)
         (template-filetypes)
         (requirejs-config-filetypes)))
