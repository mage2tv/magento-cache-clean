(ns cache.watcher
  (:require [cache.core :as core]
            [log.log :as log]
            [magento.app :as mage]
            [cache.cache :as cache]))

(set! *warn-on-infer* true)

(defonce watching? (atom false))

(defn ^:export watch [dir loglevel]
  (when-not @watching?
    (log/set-verbosity! loglevel)
    (mage/set-base-dir! dir)
    (core/process ["--watch" "--verbosity" @log/verbosity "-d" (mage/base-dir)])
    (reset! watching? true))
  nil)

(defn ^:export cleanTags [dir loglevel & tags]
  (when (seq tags)
    (when-not @watching?
      (log/set-verbosity! loglevel)
      (mage/set-base-dir! dir))
    (cache/clean-cache-types tags))
  nil)

(defn ^:export cleanIds [dir loglevel & ids]
  (when (seq ids)
    (when-not @watching?
      (log/set-verbosity! loglevel)
      (mage/set-base-dir! dir))
    (cache/clean-cache-ids ids))
  nil)

(defn ^:export cleanAll [dir loglevel]
  (when-not @watching?
    (log/set-verbosity! loglevel)
    (mage/set-base-dir! dir))
  (cache/clean-cache-types [])
  nil)


(comment
  (def log-level {:debug  3
                  :info   2
                  :notice 1
                  :error 0})
  (cleanTags "/Users/vinai/Workspace/mage2tv/m2ce" (:debug log-level) "layout" "config")
  (cleanTags "/Users/vinai/Workspace/mage2tv/m2ce" (:debug log-level))

  (cleanIds "/Users/vinai/Workspace/mage2tv/m2ce" (:debug log-level) "global__event_config_cache" "webapi_config")
  (cleanIds "/Users/vinai/Workspace/mage2tv/m2ce" (:debug log-level))

  (cleanAll "/Users/vinai/Workspace/mage2tv/m2ce" (:debug log-level))

  (watch "/Users/vinai/Workspace/mage2tv/m2ce" (:debug log-level)))




