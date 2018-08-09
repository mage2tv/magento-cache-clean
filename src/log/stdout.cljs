(ns log.stdout)

(defn out [level & msg]
  (apply println msg))
