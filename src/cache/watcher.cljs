(ns cache.watcher
  (:require [cache.core :as core]
            [log.log :as log]
            [magento.app :as mage]))

(set! *warn-on-infer* true)

(defn ^:export set-base-dir [dir]
  (mage/set-base-dir! dir)
  nil)

(defn ^:export set-log-level [level]
  (log/set-verbosity! level)
  nil)

(defn ^:export watch []
  (core/process ["--watch" "--verbosity" @log/verbosity "-d" (mage/base-dir)]))
