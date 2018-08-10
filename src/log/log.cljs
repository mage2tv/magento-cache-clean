(ns log.log
  (:require [log.stdout :as impl]))

(def levels {:log/debug 3
             :log/info 2
             :log/notice 1
             :log/error 0
             :log/all -1})

(defn- ->level [n]
  (when (keyword? n)
    (get levels n)))

(defonce verbosity (atom (->level :log/error)))

(defn- out [level & msg]
  (when (>= @verbosity (->level level)
            (apply impl/out level msg))))

(defn set-verbosity! [v]
  (reset! verbosity v))

(defn inc-verbosity! []
  (set-verbosity! (inc @verbosity)))

(defn debug [msg & msgs]
  (apply out :log/debug msg msgs))

(defn info [msg & msgs]
  (apply out :log/info msg msgs))

(defn notice [msg & msgs]
  (apply out :log/notice msg msgs))

(defn error [msg & msgs]
  (apply out :log/error msg msgs))

(defn always [msg & msgs]
  (apply out :log/all msg msgs))
