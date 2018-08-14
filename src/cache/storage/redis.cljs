(ns cache.storage.redis
  (:require [file.system :as fs]
            [magento.app :as mage]
            [cache.storage :as storage]
            [log.log :as log]
            [clojure.string :as string]))

(defonce redis (js/require "redis"))

(comment (defonce ^:const set-ids "zc:ids")
         (defonce ^:const set-tags "zc:tags")
         (defonce ^:const prefix-key "zc:k:")
         (defonce ^:const prefix-tag-id "zc:ti:"))

(def set-ids "zc:ids")
(def set-tags "zc:tags")
(def prefix-key "zc:k:")
(def prefix-tag-id "zc:ti:")

(defn- localhost? [host]
  (#{"localhost" "127.0.0.1"} host))

(defn- default-port? [port]
  (= "6379" port))

(defn- connect-db [config]
  (or (-> config :backend_options :database) 0))

(defn- connect-options [config]
  (let [{:keys [server port password]} (:backend_options config)
        database (connect-db config)]
    (cond-> {}
      (and server (not (localhost? server))) (assoc :host server)
      (and port (not (default-port? port))) (assoc :port port)
      password (assoc :auth_pass password)
      database (assoc :db database))))

(defn- tag->ids [^js/RedisClient client tag callback]
  (.smembers client (str prefix-tag-id tag)
             (fn [err ids]
               (if err
                 (log/error err)
                 (callback (js->clj ids))))))

(defn- delete-tag-and-ids [^js/RedisClient client tag ids]
  ;; TODO: make multi command
  (when (seq ids)
    (run! #(log/debug "Cleaning id" %) ids)
    (apply js-invoke client "del" (doall (map #(str prefix-key %) ids)))
    (apply js-invoke client "srem" set-ids ids))
  (.del client (str prefix-tag-id tag))
  (.srem client set-tags tag))

(defrecord Redis [^js/RedisClient client database]
  storage/CacheStorage

  (clean-tag [this tag]
    (let [callback (partial delete-tag-and-ids client tag)]
      (tag->ids client tag callback)))

  (clean-all [this]
    (log/debug "Flushing redis db" database)
    (.flushdb client)))

(defn create [config]
  (let [options (connect-options config)
        client (.createClient redis (clj->js options))]
    (->Redis client (connect-db config))))
