(ns cache.run
  (:require [cache.core]))

(defn -main [& args]
  (apply cache.core/-main args))

(set! *main-cli-fn* -main)
