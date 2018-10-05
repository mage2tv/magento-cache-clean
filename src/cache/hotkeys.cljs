(ns cache.hotkeys
  (:require [log.log :as log]
            [cache.cache :as cache]
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

(defn- process-keys [key-chan]
  (go-loop []
    (let [key (<! key-chan)]
      (check-abort key)
      (log/debug "Key pressed:" key)
      (when-let [types (get key->cachetypes key)]
        (cache/clean-cache-types types))
      (recur))))

(defn- init-hotkeys [^js/net.Socket stdin]
  (let [key-chan (chan 1)]
    (try
      (read-keys stdin key-chan)
      (process-keys key-chan)
      (catch :default e
        (close! key-chan)
        (log/error "Error initializing hotkey support:" (str e))))))

(defn observe-keys! []
  (let [stdin (.-stdin process)]
    (if (.-isTTY stdin)
      (init-hotkeys stdin)
      (log/notice "STDIN is not attached to terminal session - hotkeys disabled"))))
