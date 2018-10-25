(ns magento.static-content
  (:require [magento.app :as mage]
            [file.system :as fs]
            [log.log :as log]))

(defn static-content-base-dir []
  (str (mage/base-dir) "pub/static/"))

(defn view-preprocessed-base-dir []
  (str (mage/base-dir) "var/view_preprocessed/pub/static/"))

(defn rm-dir [dir]
  (when (fs/dir? dir)
    (fs/rmdir-recursive dir)
    (log/debug "Removed" dir)))

(defn clean [area]
  (let [dir (str (static-content-base-dir) area)]
    (log/notice "Removing static content area" area)
    (when (fs/dir? dir)
      (rm-dir dir)
      (rm-dir (str (view-preprocessed-base-dir) area))
      (rm-dir (str (view-preprocessed-base-dir) "app"))
      (rm-dir (str (view-preprocessed-base-dir) "vendor")))))
