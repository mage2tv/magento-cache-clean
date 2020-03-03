(ns cache.exports
  (:require [cache.core :as core]))

(set! *warn-on-infer* true)

(defn ^:export watch [dir]
  (core/process ["--watch" "-d" dir]))