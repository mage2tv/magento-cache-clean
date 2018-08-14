(ns cache.run
  (:require [cache.core]))

(set! *warn-on-infer* true)

(defn -main [& args]
  (apply cache.core/-main args))

(set! *main-cli-fn* -main)
