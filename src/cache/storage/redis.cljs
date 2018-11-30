(ns cache.storage.redis
  (:require [cache.storage :as storage]
            [log.log :as log]
            [cljs.core.async :refer [go-loop timeout <!]]))

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

(defn- unix-socket [server]
  (and server (second (re-find #"^(?:unix://)?(/.+)" server))))

(defn- tcp-server [server]
  (and server (second (re-find #"^(?:tcp://)?([^/]+)" server))))

(defn- server-options [config]
  (let [{:keys [server port]} (:backend_options config)
        unix-socket (unix-socket server)
        tcp-server (tcp-server server)]
    (if unix-socket {:path unix-socket}
        (cond-> {}
          (and tcp-server (not (localhost? tcp-server))) (assoc :host tcp-server)
          (and port (not (default-port? port))) (assoc :port port)))))

(defn- connect-options [config]
  (let [password (-> config :backend_options :password)
        database (connect-db config)]
    (cond-> (server-options config)
      password (assoc :auth_pass password)
      database (assoc :db database))))

(defn- tag->ids [^js/RedisClient client tag callback]
  (.smembers client (str prefix-tag-id tag)
             (fn [err ids]
               (if err
                 (log/error err)
                 (callback (js->clj ids))))))

(defn- prefix-keys [ids]
  (doall (map #(str prefix-key %) ids)))

(defn- delete-cache-ids [^js/RedisClient client ids]
  (apply log/debug "Cleaning id(s):" ids)
  (apply js-invoke client "del" (prefix-keys ids))
  (apply js-invoke client "srem" set-ids ids))

(defn- delete-tag-and-ids [^js/RedisClient client tag ids]
  ;; TODO: make multi command
  (when (seq ids)
    (log/debug "About to clean" (count ids) "id(s)")
    (run! #(delete-cache-ids client %) (partition-all 500 ids)))
  (.del client (str prefix-tag-id tag))
  (.srem client set-tags tag))

(defrecord Redis [^js/RedisClient client database id-prefix n-pending]
  storage/CacheStorage

  (clean-tag [this tag]
    (swap! n-pending inc)
    (let [callback (fn [ids]
                     (delete-tag-and-ids client tag ids)
                     (swap! n-pending dec))]
      (tag->ids client tag callback)))

  (clean-id [this id]
    (delete-cache-ids client [id]))

  (clean-all [this]
    (log/debug "Flushing redis db" database)
    (.flushdb client))

  (close [this]
    (go-loop [n @n-pending]
        (if (zero? n)
          (do (log/debug "Disconnecting redis client")
              (.quit client (fn[])))
          (do (<! (timeout 15))
              (recur @n-pending))))))

(defn create [config]
  (let [options (connect-options config)
        id-prefix (:id_prefix config)
        client (.createClient redis (clj->js options))
        n-pending-tasks (atom 0)]
    (->Redis client (connect-db config) id-prefix n-pending-tasks)))


#_(def client (:client (create {:backend "Cm_Cache_Backend_Redis", :backend_options {:server "localhost", :database 0, :port 6379}})))
#_(def c (create {:backend "Cm_Cache_Backend_Redis", :backend_options {:server "localhost", :database 0, :port 6379}}))
