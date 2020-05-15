(ns php.reader
  (:require [clojure.string :as string]
            [file.system :as fs]))

(defn php-namespace [php]
  (second (re-find #"(?mi)^\s*namespace\s+([a-z0-9\\]+)" (str php))))

(defn php-class [php]
  (second (re-find #"(?mi)^\s*class\s+(\w+)" (str php))))

(defn php-interface [php]
  (second (re-find #"(?mi)^\s*interface\s+(\w+)" (str php))))

(defn php-methods [php]
  (map second (re-seq #"\bpublic\s+function\s+(\w+)\(" php)))

(defn file->php-class [file]
  (try
    (let [maybe-php (fs/head file 2048)
          namespace (php-namespace maybe-php)
          class (or (php-class maybe-php) (php-interface maybe-php))]
      (when (and namespace class)
        (str "\\" namespace "\\" class)))
    (catch :default e)))

(defn php-class->file [php-class]
  (str (string/replace php-class "\\" "/") ".php"))
