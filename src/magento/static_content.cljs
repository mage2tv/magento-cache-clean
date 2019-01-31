(ns magento.static-content
  (:require [magento.app :as mage]
            [file.system :as fs]
            [log.log :as log]))

(defn static-content-base-dir []
  (str (mage/base-dir) "pub/static/"))

(defn static-content-area-dir [area]
  (str (static-content-base-dir) area))

(defn view-preprocessed-base-dir []
  (str (mage/base-dir) "var/view_preprocessed/pub/static/"))

(defn view-preprocessed-area-dir [area]
  (str (view-preprocessed-base-dir) area))

(defn static-content-theme-locale-dirs [area]
  )

(defn rm-dir [dir]
  (when (fs/dir? dir)
    (fs/rmdir-recursive dir)
    (log/debug "Removed" dir)))

(defn clean [area]
  (log/notice "Removing static content area" area)
  (rm-dir (static-content-area-dir area))
  (rm-dir (view-preprocessed-area-dir area))
  (rm-dir (view-preprocessed-area-dir "app"))
  (rm-dir (view-preprocessed-area-dir "vendor")))

#_(defn- requirejs-config-files [area]
  )
