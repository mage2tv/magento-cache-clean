(ns log.stdout)

(defn determine-out [level]
  (case level
    :log/error *print-err-fn*
    *print-fn*))

(defn out [level & msg]
  (binding [*print-fn* (determine-out level)]
    (apply println msg)))
