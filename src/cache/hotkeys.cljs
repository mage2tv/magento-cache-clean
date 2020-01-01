(ns cache.hotkeys
  (:require [log.log :as log]
            [cache.cache :as cache]
            [magento.static-content :as static-content]
            [magento.generated-code]
            [cljs.core.async :refer [go-loop <! put! close! chan]]))

(def ctr-c \u0003)

(def process (js/require "process"))

(def key->cachetypes {"c" ["config"]
                      "b" ["block_html"]
                      "l" ["layout"]
                      "f" ["full_page"]
                      "a" [] ;; a for all
                      "v" ["block_html" "layout" "full_page"] ;; v for view
                      "t" ["translate"]})

(def key->static-content-areas {"F" ["frontend"]
                                "A" ["adminhtml"]})

(def key-generated-code "G")

(defn- prep-stdin [^js/net.Socket stdin]
  (.resume stdin)
  (.setEncoding stdin "utf8")
  (.setRawMode stdin true))

(defn- read-keys [^js/net.Socket stdin key-chan]
  (prep-stdin stdin)
  (.on stdin "data" (fn [data] (put! key-chan data)))
  (log/debug :without-time "Listening for hotkeys"))

(defn- check-abort [key]
  (when (= key ctr-c)
    (log/notice :without-time "Bye!")
    (.exit process)))

(defn- process-key [base-dir key]
  (log/debug "Key pressed:" key)
  (check-abort key)
  (when-let [types (get key->cachetypes key)]
    (cache/clean-cache-types types))
  (doseq [area (get key->static-content-areas key)]
    (static-content/clean base-dir area))
  (when (= key-generated-code key)
    (magento.generated-code/clean base-dir)))

(defn- process-keys [base-dir key-chan]
  (go-loop []
    (let [key (<! key-chan)]
      (process-key base-dir key)
      (recur))))

(defn- init-hotkeys [base-dir ^js/net.Socket stdin]
  (let [key-chan (chan 1)]
    (try
      (read-keys stdin key-chan)
      (process-keys base-dir key-chan)
      true
      (catch :default e
        (close! key-chan)
        (log/error :without-time "Error initializing hotkey support:" (str e))
        false))))

(defn observe-keys! [base-dir]
  (let [stdin (.-stdin process)]
    (if (.-isTTY stdin)
      (init-hotkeys base-dir stdin)
      (do
        (log/notice :without-time "STDIN is not attached to terminal session - hotkeys disabled!")
        false))))
