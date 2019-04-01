(ns cache.config
  (:require [cache.config.php :as php]))

(defn read-app-config
  "Return the app/etc/env.php configuration as keywordized EDN"
  [magento-basedir]
  (php/read-app-config magento-basedir))

(defn list-component-dirs
  "Return a seq of all module or theme dirs"
  [magento-basedir type]
  (php/list-component-dirs magento-basedir type))
