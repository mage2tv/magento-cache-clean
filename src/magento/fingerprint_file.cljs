(ns magento.fingerprint-file
  (:require [file.system :as fs]
            [clojure.string :as string]))

(defn- match-name? [name-pattern file]
  (cond
    (regexp? name-pattern) (re-find name-pattern file)
    :else (= name-pattern (fs/basename file))))

(defn- file-fingerprint-fn [name-pattern]
  (fn [file]
    (match-name? name-pattern file)))

(defn- filenames->fingerprint-fns [type filenames]
  (reduce (fn [acc filename]
            (assoc acc (file-fingerprint-fn filename) type)) {} filenames))

(defn- config-filetypes []
  (let [res [#"/etc(?:/[^/]+|)/di\.xml$"
             #"/etc/crontab\.xml$"
             #"/etc(?:/[^/]+|)/events\.xml$"
             #"/etc/extension_attributes\.xml$"
             #"/etc/(?:[^/]+)/routes\.xml$"
             #"/etc/widget\.xml$"
             #"/etc/product_types\.xml$"
             #"/etc/product_options\.xml$"
             #"/etc/payment\.xml$"
             #"/etc/search_request\.xml$"
             #"/etc/config\.xml$"
             #"/ui_component/.+\.xml$"
             #"/etc/acl\.xml$"
             #"/etc/adminhtml/system\.xml$"
             #"/etc/indexer\.xml$"]]
    (filenames->fingerprint-fns ::config res)))

(defn- layout-filetypes []
  (let [res [#"/layout/.+\.xml$"
             #"/page_layout/.+\.xml$"]]
    (filenames->fingerprint-fns ::layout res)))

(defn- translation-filetypes []
  (let [res [#"/i18n/.+\.csv$"]]
    (filenames->fingerprint-fns ::translation res)))

(defn- template-filetypes []
  (let [res [#"/templates/.+\.phtml$"
             #"/etc/view\.xml$"
             #"/theme\.xml$"]]
    (filenames->fingerprint-fns ::template res)))

(defn- requirejs-config-filetypes []
  (let [res [#"/view/(?:base|frontend|adminhtml)/requirejs-config\.js$"]]
    (filenames->fingerprint-fns ::requirejs-config res)))

(defn- menu-filetypes []
  (let [res [#"/etc/adminhtml/menu\.xml$"]]
    (filenames->fingerprint-fns ::menu res)))

(def file->type
  (merge (config-filetypes)
         (layout-filetypes)
         (translation-filetypes)
         (template-filetypes)
         (menu-filetypes)
         (requirejs-config-filetypes)))
