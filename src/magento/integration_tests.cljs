(ns magento.integration-tests
  (:require [file.system :as fs]
            [log.log :as log]
            [clojure.string :as string]))

(def sandbox-path "/dev/tests/integration/tmp")

(defn clean [base-dir]
  (let [tmp-dir (str base-dir sandbox-path)]
    (if (fs/dir? tmp-dir)
      (do (log/notice "Removing integration-test sandbox directories from" tmp-dir)
        (->> tmp-dir
             (fs/ls)
             (filter #(string/includes? % (str sandbox-path "/sandbox-")))
             (run! #(fs/rmdir-recursive %))))
      (log/notice "Integration test tmp directory" tmp-dir "not found"))))