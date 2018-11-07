(ns magento.generated-code
  (:require [file.system :as fs]
            [magento.app :as app]
            [log.log :as log]
            [clojure.string :as string]))

(defn- php-namespace [php]
  (second (re-find #"(?mi)^\s*namespace\s+([a-z0-9\\]+)" (str php))))

(defn- php-class [php]
  (second (re-find #"(?mi)^\s*class\s+(\w+)" (str php))))

(defn- file->php-class [file]
  (let [maybe-php (fs/head file 2048)
        namespace (php-namespace maybe-php)
        class (php-class maybe-php)]
    (when (and namespace class)
      (str "\\" namespace "\\" class))))

(defn- php-class->file [php-class]
  (str (string/replace php-class "\\" "/") ".php"))

(defn generated-code-dir [base-dir]
  (some fs/dir? [(str base-dir "generated/code") (str base-dir "var/generated")]))

(defn- interface? [type]
  (= "Interface" (subs type (- (count type) (count "Interface")))))

(defn- class-without-interface [type]
  (when (interface? type)
    (subs type 0 (- (count type) (count "Interface")))))

(defn maybe-generated-classes [base-class]
  (let [generated-types [(str base-class "Converter")
                         (str base-class "InterfaceFactory")
                         (str base-class "Factory")
                         (str base-class "\\Interceptor")
                         (str base-class "\\Logger")
                         (str base-class "Mapper")
                         (str base-class "Persistor")
                         (str base-class "\\Proxy")]]
    (if-let [without-interface (class-without-interface base-class)]
      (into generated-types [(str without-interface "Extension")
                             (str without-interface "ExtensionInterface")
                             (str without-interface "\\Repository")])
      generated-types)))

(defn- php-class->generated-file [generated-code-dir class-name]
  (str generated-code-dir (php-class->file class-name)))

(defn- generated-files [php-class]
  (when-let [dir (generated-code-dir (app/base-dir))]
    (->> php-class
         maybe-generated-classes
         (map #(php-class->generated-file dir %))
         (filter fs/exists?))))

(defn php-file->generated-code-files [php-file]
  (when-let [php-class (file->php-class php-file)]
    (generated-files php-class)))
