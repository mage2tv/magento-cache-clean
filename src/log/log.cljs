(ns log.log
  (:require [log.stdout :as impl]
            [goog.date :as date]))


(def levels {:log/debug  3
             :log/info   2
             :log/notice 1
             :log/error  0
             :log/all    -1})

(defn- ->level [n]
  (when (keyword? n)
    (get levels n)))

(defonce verbosity (atom (->level :log/error)))

(defn- out [level & msgs]
  (when (>= @verbosity (->level level)
            (apply impl/out level msgs))))

(defn set-verbosity! [v]
  (reset! verbosity v))

(defn inc-verbosity! []
  (set-verbosity! (inc @verbosity)))


(defn timestring []
  (.toIsoTimeString (date/DateTime.)))

(defn log-time [level & msgs]
  (if (= :without-time (first msgs))
    (apply out level (rest msgs))
    (apply out level (cons (timestring) msgs))))

(defn debug [msg & msgs]
  (apply log-time :log/debug msg msgs))

(defn info [msg & msgs]
  (apply log-time :log/info msg msgs))

(defn notice [msg & msgs]
  (apply log-time :log/notice msg msgs))

(defn error [msg & msgs]
  (apply log-time :log/error msg msgs))

(defn always [msg & msgs]
  (apply log-time :log/all msg msgs))
