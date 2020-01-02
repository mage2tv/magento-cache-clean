(ns cache.storage.varnish
  (:require [cache.storage :as storage]
            [log.log :as log]))

(defonce http (js/require "http"))

(defonce enabled? (atom true))

(defn- purge-options [server pattern]
  {:protocol "http:"
   :hostname (:host server "localhost")
   :port (:port server "80")
   :method "PURGE"
   :path "/"
   :headers {"X-Magento-Tags-Pattern" pattern}
   :timeout 10000})

(defn request [options success-callback error-callback]
  (doto (.request http options success-callback)
    (.on "error" error-callback)
    (.end)))

(defn- handle-varnish-response [res]
  (let [status (int (.-statusCode res))]
    (when-not (= 200 status)
      (log/notice "Varnish response code" status))
    (.on res "data" #(when-not (= 200 status)
                       (log/notice "Response:" %)))))

(defn- handle-varnish-error [e]
  (log/debug "Varnish request error: " (.-message e))
  (log/info "No further Varnish PURGEs will be attempted")
  (reset! enabled? false))

(defn- purge-with-pattern!
  "Send Varnish PURGE request with the given X-Magento-Tags-Pattern header.

  Reference PHP implementation at
  \\Magento\\CacheInvalidate\\Model\\PurgeCache::sendPurgeRequest"
  [server pattern]
  (when @enabled?
    (let [options (clj->js (purge-options server pattern))]
      (log/debug "Varnish request:" options)
      (request options handle-varnish-response handle-varnish-error))))

(defn- tags->pattern
  "Transform the given tag or tags into a regular expression pattern for use in
  the X-Magento-Tags-Pattern header.

  Reference PHP implementation at
  \\Magento\\CacheInvalidate\\Observer\\InvalidateVarnishObserver::execute"
  [tags]
  (if (string? tags)
    (tags->pattern [tags])
    (apply str (interpose "|" (map #(str "((^|,)" % "(,|$))") (distinct tags))))))

(defn purge-tags! [server tags]
  (purge-with-pattern! server (tags->pattern tags)))

(defn purge-all! [server]
  (purge-with-pattern! server ".*"))

(defrecord Varnish [servers]
  storage/CacheStorage

  (clean-tag [this tag]
    (run! #(purge-tags! % [tag]) servers))

  (clean-id [this id]
    (log/notice (str "Deleting specific cache ID is not supported "
                     "with the varnish backend")))

  (clean-all [this]
    (run! purge-all! servers))

  (close [this]))

(defn create [servers]
  (->Varnish servers))
