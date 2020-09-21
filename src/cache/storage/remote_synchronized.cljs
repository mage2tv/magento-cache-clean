(ns cache.storage.remote-synchronized
  (:require [cache.storage :as storage]
            [log.log :as log]))


(defrecord RemoteSynchronized [local remote]
  storage/CacheStorage

  (clean-tag [this tag]
    (log/debug "Delegating cleaning of tag" tag "to remote backend")
    (storage/clean-tag remote tag)
    (log/debug "Delegating cleaning of tag" tag "to local backend")
    (storage/clean-tag local tag))
  (clean-id [this id]
    (log/debug "Delegating cleaning of id" id "to remote backend")
    (storage/clean-id remote id)
    (log/debug "Delegating cleaning of id" id "to local backend")
    (storage/clean-id local id))
  (clean-all [this]
    (log/debug "Flushing remote backend")
    (storage/clean-all remote)
    (log/debug "Flushing local backend")
    (storage/clean-all local))
  (close [this]
    (log/debug "Closing remote backend")
    (storage/close remote)
    (log/debug "Closing local backend")
    (storage/close local)))


(defn create [local remote]
  (map->RemoteSynchronized {:local local
                            :remote remote}))

(defn extract-local-config [synchronized-config]
  {:backend (-> synchronized-config :backend_options :local_backend)
   :id_prefix (:id_prefix synchronized-config)
   :backend_options (-> synchronized-config :backend_options :local_backend_options)})

(defn extract-remote-config [synchronized-config]
  {:backend (-> synchronized-config :backend_options :remote_backend)
   :id_prefix (:id_prefix synchronized-config)
   :backend_options (-> synchronized-config :backend_options :remote_backend_options)})

#_(def test-config {:id_prefix "69d_",
                    :backend "\\Magento\\Framework\\Cache\\Backend\\RemoteSynchronizedCache",
                    :backend_options {:remote_backend "Magento\\Framework\\Cache\\Backend\\Redis",
                                      :remote_backend_options {:server "redis",
                                                               :database 0,
                                                               :port 6379,
                                                               :password "",
                                                               :compress_data 1,
                                                               :compression_lib ""},
                                      :local_backend "Cm_Cache_Backend_File",
                                      :local_backend_options {:cache_dir "/dev/shm/",
                                                              :file_name_prefix "pc"}}})