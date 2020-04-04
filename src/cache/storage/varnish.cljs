(ns cache.storage.varnish
  (:require [clojure.string :as string]
            [cache.storage :as storage]
            [log.log :as log]))

(defonce http (js/require "http"))

(defonce enabled? (atom true))

(defn- purge-options [server pattern]
  {:protocol "http:"
   :hostname (:host server "localhost")
   :port     (:port server "80")
   :method   "PURGE"
   :path     "/"
   :headers  {"X-Magento-Tags-Pattern" pattern}
   :timeout  10000})

(defn request [options success-callback error-callback]
  (doto (.request http options success-callback)
    (.on "error" error-callback)
    (.end)))

(defn in-body-status [body]
  (let [status (re-find #"<title>(\d+).+</title>" body)]
    (int (second status))))

(defn- handle-varnish-response
  "Successful responses might have status code 200 or o.
  If the response code is 0, then the body should contain \"200 Purged\".
  (The latter is produced if synth() is used in the vcl to generate a synthetic response.)"
  [res]
  (let [status (int (.-statusCode res))]
    (.on res "data" (fn [buffer]
                      (let [body (str buffer)
                            status (if (= 0 status) (in-body-status body) status)]
                        (when-not (= 200 status)
                          (log/notice "Varnish response code" status)
                          (log/notice "Response:" body)))))))

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
