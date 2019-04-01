(ns cache.config.php
  (:require [file.system :as fs]
            [goog.json :as json]
            [clojure.string :as string]))

(defonce child-process (js/require "child_process"))


(defn- env-config-cmd [magento-basedir]
  (let [magento-basedir (fs/add-trailing-slash magento-basedir)
        app-etc-env (str magento-basedir "app/etc/env.php")]
    (when-not (fs/exists? app-etc-env)
      (throw (ex-info (str "File app/etc/env.php not found: " app-etc-env) {})))
    (str "php -r "
         "\"echo json_encode("
         "(require '" app-etc-env "') ?? []"
         ");\"")))

(defn read-app-config [magento-basedir]
  (let [cmd (env-config-cmd magento-basedir)
        output (.execSync child-process cmd)
        config (js->clj (json/parse output) :keywordize-keys true)]
    (into {} config)))


(defn- unescape-php-var-on-win-os [php]
  (cond-> php (fs/win?) (string/replace #"\\\$" "$")))

(defn- list-components-cmd [magento-basedir type]
  (let [composer-autoload (str magento-basedir "vendor/autoload.php")]
    (when-not (fs/exists? composer-autoload)
      (throw (ex-info (str "Composer autoload.php not found: " composer-autoload) {})))
    (unescape-php-var-on-win-os
     (str "php -r "
          "\"require '" composer-autoload "'; "
          "foreach ((new \\Magento\\Framework\\Component\\ComponentRegistrar)->getPaths('" type "') as \\$m) "
          "echo \\$m.PHP_EOL;\""))))

(defn list-component-dirs [magento-basedir type]
  (let [magento-basedir (fs/add-trailing-slash magento-basedir)
        cmd (list-components-cmd magento-basedir type)
        output (.execSync child-process cmd)]
    (string/split-lines output)))
