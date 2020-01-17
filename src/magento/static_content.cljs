(ns magento.static-content
  (:require [file.system :as fs]
            [log.log :as log]))

(defn static-content-base-dir [base-dir]
  (str base-dir "pub/static/"))

(defn static-content-area-dir [base-dir area]
  (str (static-content-base-dir base-dir) area))

(defn view-preprocessed-base-dir [base-dir]
  (str base-dir "var/view_preprocessed/pub/static/"))

(defn view-preprocessed-area-dir [base-dir area]
  (str (view-preprocessed-base-dir base-dir) area))

(defn static-content-theme-locale-dirs [base-dir area]
  (filter fs/dir? (fs/ls-dive (static-content-area-dir base-dir area) 2)))

(defn view-preprocessed-theme-locale-dirs [base-dir area]
  (filter fs/dir? (fs/ls-dive (view-preprocessed-area-dir base-dir area) 2)))

(defn- static-files-in-locale-dir [base-dir area file]
  (let [dirs (into (static-content-theme-locale-dirs base-dir area)
                   (view-preprocessed-theme-locale-dirs base-dir area))]
    (filter #(= file (fs/basename %)) (mapcat fs/ls dirs))))

(defn js-translation-files [base-dir area]
  (static-files-in-locale-dir base-dir area "js-translation.json"))

(defn requirejs-config-files [base-dir area]
  (static-files-in-locale-dir base-dir area "requirejs-config.js"))

(defn- rm-files [dir]
  (when (fs/dir? dir)
    (fs/rm-files-recursive dir)
    (log/debug "Removed files in" dir)))

(defn clean [base-dir area]
  (log/notice "Removing static content area" area)
  (rm-files (static-content-area-dir base-dir area))
  (rm-files (view-preprocessed-area-dir base-dir area))
  (rm-files (view-preprocessed-area-dir base-dir "app"))
  (rm-files (view-preprocessed-area-dir base-dir "vendor")))


