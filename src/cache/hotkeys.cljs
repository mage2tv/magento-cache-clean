(ns cache.hotkeys
  (:require [log.log :as log]
            [cache.cache :as cache]
            [magento.static-content :as static-content]
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
  (log/debug "Listening for hotkeys"))

(defn- check-abort [key]
  (when (= key ctr-c)
    (log/notice "Bye!")
    (.exit process)))

(defn- process-key [key]
  (log/debug "Key pressed:" key)
  (check-abort key)
  (when-let [types (get key->cachetypes key)]
    (cache/clean-cache-types types))
  (doseq [area (get key->static-content-areas key)]
    (static-content/clean area))
  (when (= key-generated-code key)
    (magento.generated-code/clean)))

(defn- process-keys [key-chan]
  (go-loop []
    (let [key (<! key-chan)]
      (process-key key)
      (recur))))

(defn- init-hotkeys [^js/net.Socket stdin]
  (let [key-chan (chan 1)]
    (try
      (read-keys stdin key-chan)
      (process-keys key-chan)
      true
      (catch :default e
        (close! key-chan)
        (log/error "Error initializing hotkey support:" (str e))
        false))))

(defn observe-keys! []
  (let [stdin (.-stdin process)]
    (if (.-isTTY stdin)
      (init-hotkeys stdin)
      (do
        (log/notice "STDIN is not attached to terminal session - hotkeys disabled!")
        false))))
