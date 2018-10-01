(ns cache.hotkeys
  (:require [log.log :as log]
            [cache.cache :as cache]
            [cljs.core.async :refer [go-loop <! put! chan]]))

(def ctr-c \u0003)

(def process (js/require "process"))

(def key->cachetypes {"c" ["config"]
                      "b" ["block_html"]
                      "l" ["layout"]
                      "f" ["full_page"]
                      "a" [] ;; a for all
                      "v" ["block_html" "layout" "full_page"] ;; v for view
                      "t" ["translate"]})

(defn- read-keys [key-chan]
  (let [stdin (.-stdin process)]
    (.resume stdin)
    (.setEncoding stdin "utf8")
    (.setRawMode stdin true)
    (.on stdin "data" (fn [data] (put! key-chan data)))
    (log/debug "Listening for hotkeys")))

(defn- process-keys [key-chan]
  (go-loop []
    (let [key (<! key-chan)]

      (when (= key ctr-c)
        (log/notice "Bye!")
        (.exit process))

      (log/debug "Key pressed:" key)
      (when-let [types (get key->cachetypes key)]
        (cache/clean-cache-types types))
      (recur))))

(defn observe-keys! []
  (let [key-chan (chan 1)]
    (read-keys key-chan)
    (process-keys key-chan)
    key-chan))
