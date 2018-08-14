(ns cache.storage.redis
  (:require [file.system :as fs]
            [magento.app :as mage]
            [cache.storage :as storage]
            [log.log :as log]
            [clojure.string :as string]))

(defonce redis (js/require "redis"))

(def ^:const set-ids "zc:ids")
(def ^:const set-tags "zc:tags")
(def ^:const prefix-key "zc:k:")
(def ^:const prefix-tag-id "zc:ti:")

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

(defn- tag->ids [client tag callback]
  (.smembers client (str prefix-tag-id tag)
             (fn [err ids]
               (callback ids))))

(defn- delete-tag-and-ids [client tag ids]
  ;; TODO: make multi command
  (run! #(log/debug "Cleaning id" %) ids)
  (.del client (doall (map #(str prefix-key %) ids)))
  (.srem client set-ids ids)
  (.del client (str prefix-tag-id tag))
  (.srem client set-tags tag))

(defrecord Redis [client database]
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
