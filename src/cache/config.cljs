(ns cache.config
  (:require [cache.config.php :as php]
            [cache.config.file :as file]))

(defn- use-dump? [magento-basedir]
  (file/config-dump-exists? magento-basedir))

(defn- file-or-php [magento-basedir file-fn php-fn]
  (let [f (if (use-dump? magento-basedir) file-fn php-fn)]
    (partial f magento-basedir)))

(defn- read-config-fn [magento-basedir]
  (file-or-php magento-basedir file/read-app-config php/read-app-config))

(defn read-app-config
  "Return the app/etc/env.php configuration as keywordized EDN"
  [magento-basedir]
  (let [read-config (read-config-fn magento-basedir)]
    (read-config)))

(defn- list-components-fn [magento-basedir]
  (file-or-php magento-basedir file/list-component-dirs php/list-component-dirs))

(defn list-component-dirs
  "Return a seq of all module or theme dirs"
  [magento-basedir type]
  (let [list-component-dirs (list-components-fn magento-basedir)]
    (list-component-dirs type)))

(defn watch-for-new-modules! [magento-basedir callback]
  (file/watch-for-new-modules! magento-basedir
                               #(when (use-dump? magento-basedir) (callback)))
  (php/watch-for-new-modules! magento-basedir
                              #(when-not (use-dump? magento-basedir) (callback))))

(defn mtime [magento-basedir]
  (let [mtime (file-or-php magento-basedir file/mtime php/mtime)]
    (mtime magento-basedir)))