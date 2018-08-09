(ns log.log
  (:require [log.stdout :as impl]))

(def levels {::debug 3
             ::info 2
             ::notice 1
             ::error 0
             ::all -1})

(defn- ->level [n]
  (when (keyword? n)
    (get levels n)))

(defonce verbosity (atom (->level ::error)))

(defn- out [level & msg]
  (when (>= @verbosity (->level level)
            (apply impl/out level msg))))

(defn set-verbosity! [v]
  (reset! verbosity v))

(defn debug [msg & msgs]
  (apply out ::debug msg msgs))

(defn info [msg & msgs]
  (apply out ::info msg msgs))

(defn notice [msg & msgs]
  (apply out ::notice msg msgs))

(defn error [msg & msgs]
  (apply out ::error msg msgs))

(defn always [msg & msgs]
  (apply out ::all msg msgs))
