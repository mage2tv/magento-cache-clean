(ns cache.config.file
  (:require [log.log :as log]
            [file.system :as fs]
            [goog.json :as json]))

(def config-file-name "var/cache-clean-config.json")

(defn- config-file [magento-basedir]
  (str (fs/add-trailing-slash magento-basedir) config-file-name))

(defn config-dump-exists? [magento-basedir]
  (fs/exists? (config-file magento-basedir)))

(defn- parse-config-dump [json-string]
  (let [config (js->clj (json/parse json-string) :keywordize-keys true)]
    (into {} config)))

(defn- get-config-from-dump [magento-basedir]
  (parse-config-dump (fs/slurp (config-file magento-basedir))))

(defn- make-path-absolute [basedir path]
  (cond->> path
           (not= "/" (subs path 0 1)) (str basedir)))

(defn list-component-dirs [magento-basedir type]
  (log/debug :without-time (str "Listing " type "s from " config-file-name))
  (let [config (get-config-from-dump magento-basedir)]
    (map #(make-path-absolute (fs/add-trailing-slash magento-basedir) %)
         (case type
           "module" (:modules config)
           "theme" (:themes config)))))

(defn read-app-config [magento-basedir]
  (log/debug :without-time (str "Reading app config from " config-file-name))
  (let [config (get-config-from-dump magento-basedir)]
    (:app config)))

(defn watch-for-new-modules! [magento-basedir callback]
  (let [config-file (config-file magento-basedir)
        dir (fs/dirname config-file)
        file (fs/basename config-file)]

    (if-not (fs/file? config-file)
      (log/debug :without-time "Config dump file" config-file "not found")
      (do
        (log/debug :without-time "Monitoring" config-file "for new modules")
        (fs/watch dir #(when (= file (fs/basename %)) (callback)))))))

(defn mtime [magento-basedir]
  (fs/mtime (config-file magento-basedir)))