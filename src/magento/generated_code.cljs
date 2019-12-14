(ns magento.generated-code
  (:require [file.system :as fs]
            [log.log :as log]
            [clojure.string :as string]))

(defn- php-namespace [php]
  (second (re-find #"(?mi)^\s*namespace\s+([a-z0-9\\]+)" (str php))))

(defn- php-class [php]
  (second (re-find #"(?mi)^\s*class\s+(\w+)" (str php))))

(defn- php-interface [php]
  (second (re-find #"(?mi)^\s*interface\s+(\w+)" (str php))))

(defn- file->php-class [file]
  (let [maybe-php (fs/head file 2048)
        namespace (php-namespace maybe-php)
        class (or (php-class maybe-php) (php-interface maybe-php))]
    (when (and namespace class)
      (str "\\" namespace "\\" class))))

(defn- php-class->file [php-class]
  (str (string/replace php-class "\\" "/") ".php"))

(defn generated-code-dir [base-dir]
  (let [candidates [(str base-dir "generated/code") (str base-dir "var/generated")]]
    (some fs/dir? candidates)))

(defn- interface? [type]
  (= "Interface" (subs type (- (count type) (count "Interface")))))

(defn- class-without-interface [type]
  (when (interface? type)
    (subs type 0 (- (count type) (count "Interface")))))

(defn maybe-generated-classes [base-class]
  (let [generated-types [(str base-class "Converter")
                         (str base-class "InterfaceFactory")
                         ;;(str base-class "Factory") ;; factories don't need to be cleaned
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

(defn- generated-files-for-class [base-dir php-class]
  (when-let [dir (generated-code-dir base-dir)]
    (->> php-class
         maybe-generated-classes
         (map #(php-class->generated-file dir %))
         (filter fs/exists?))))

(defn php-file->generated-code-files [base-dir php-file]
  (when-let [php-class (file->php-class php-file)]
    (generated-files-for-class base-dir php-class)))

(defn generated-extension-attribute-classes
  "Return list of all generated files related to extension_attributes.xml contents.
  The ExtensionFactory.php is not included as it doesn't change depending on extension
  attributes configuration."
  [base-dir]
  (when-let [dir (generated-code-dir base-dir)]
    (->> dir fs/file-tree (filter #(or (string/ends-with? % "Extension.php")
                                       (string/ends-with? % "ExtensionInterface.php"))))))

(defn- without-base-path [base-dir file]
  (subs file (count base-dir)))


(defn clean [base-dir]
  (if-let [dir (generated-code-dir base-dir)]
    (do (log/notice "Removing generated code from" dir)
        (fs/rmdir-recursive dir))
    (log/debug "No generated code directory found")))

(defn remove-generated-files-based-on-php! [base-dir php-file]
  (let [files (php-file->generated-code-files base-dir php-file)]
    (when (seq files)
      (log/notice "Removing generated code from" (str base-dir ":\n")
                  (apply str (interpose ", " (map #(without-base-path base-dir %) files))))
      (run! fs/rm files))))

(defn remove-generated-extension-attributes-php! [base-dir]
  (let [files (generated-extension-attribute-classes base-dir)]
    (when (seq files)
      (log/notice "Removing all generated extension attributes classes")
      (log/debug (str "In base dir " base-dir ":\n")
                 (apply str (interpose ", " (map #(without-base-path base-dir %) files))))
      (run! fs/rm files))))