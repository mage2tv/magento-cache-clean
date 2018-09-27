(ns cache.storage.redis
  (:require [cache.storage :as storage]
            [log.log :as log]))

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
  (run! #(log/debug "Cleaning id" %) ids)
  (apply js-invoke client "del" (prefix-keys ids))
  (apply js-invoke client "srem" set-ids ids))

(defn- delete-tag-and-ids [^js/RedisClient client tag ids]
  ;; TODO: make multi command
  (when (seq ids)
    (log/debug "About to clean" (count ids) "id(s)")
    (run! #(delete-cache-ids client %) (partition-all 500 ids)))
  (.del client (str prefix-tag-id tag))
  (.srem client set-tags tag))

(defrecord Redis [^js/RedisClient client database]
  storage/CacheStorage

  (clean-tag [this tag]
    (let [callback (fn [ids]
                     (delete-tag-and-ids client tag ids))]
      (tag->ids client tag callback)))

  (clean-all [this]
    (log/debug "Flushing redis db" database)
    (.flushdb client))

  (close [this]
    (log/debug "Closing redis connection")
    (.quit client (fn[]))))

(defn create [config]
  (let [options (connect-options config)
        client (.createClient redis (clj->js options))]
    (->Redis client (connect-db config))))


#_(def client (:client (create {:backend "Cm_Cache_Backend_Redis", :backend_options {:server "localhost", :database 0, :port 6379}})))
#_(def c (create {:backend "Cm_Cache_Backend_Redis", :backend_options {:server "localhost", :database 0, :port 6379}}))
