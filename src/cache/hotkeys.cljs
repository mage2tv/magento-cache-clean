(ns cache.hotkeys
  (:require [log.log :as log]
            [cache.cache :as cache]
            [cljs.core.async :refer [go-loop <! put! chan]]))

(def key->cachetypes {"c" ["config"]
                      "b" ["block_html"]
                      "l" ["layout"]
                      "f" ["full_page"]
                      "a" []
                      "v" ["block_html" "layout" "full_page"]
                      "t" ["translate"]})


(defn- read-keys [key-chan]
  (let [stdin (.-stdin (js/require "process"))]
    (.setEncoding stdin "utf8")
    (.resume stdin)
    (.on stdin "data" #(put! key-chan %))))

(defn- process-keys [key-chan]
  (go-loop []
    (let [key (<! key-chan)]
      (when-let [types (get key->cachetypes key)]
        (apply cache/clean types)
        (recur)))))

(defn listen-for-keys! []
  (let [key-chan (chan 1)]
    (read-keys key-chan)
    (process-keys key-chan)))
