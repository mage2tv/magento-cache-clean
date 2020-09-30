(ns magento.service-contract
  (:require [util.crypto :refer [md5]]
            [file.system :as fs]
            [php.reader :as php]))

;; Reference \Magento\Framework\Reflection\MethodsMap

(def service-method-params-cache-prefix "service_method_params_")
(def service-interface-methods-cache-prefix "serviceInterfaceMethodsMap")

(defn- service-methods [php-source]
  (php/php-methods php-source))

(defn- service-name [php-source]
  (str
    (php/php-namespace php-source)
    "\\"
    (php/php-interface php-source)))

(defn service-interface? [file]
  (or (re-find #"/Api/.+Interface.php$" file)
      (re-find #"\\Api\\.+Interface.php$" file)))

(defn- method-params-cache-id [interface-name method-name]
  (str service-method-params-cache-prefix (md5 (str interface-name method-name))))

(defn- method-param-cache-ids [php-source]
  (let [interface-name (service-name php-source)
        method->id (partial method-params-cache-id interface-name)]
    (map method->id (service-methods php-source))))

(defn- methods-list-cache-id [php-source]
  (str service-interface-methods-cache-prefix "-" (md5 (service-name php-source))))

(defn service-cache-ids [file]
  (when (and (service-interface? file) (fs/exists? file))
    (try
      (let [php-source (fs/slurp file)]
        (conj (method-param-cache-ids php-source)
              (methods-list-cache-id php-source)))
      (catch :default e
        ;; the file probably was removed since the change event was triggered. Happens on synchronized fs sometimes.
        []))))
