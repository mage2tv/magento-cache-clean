(ns cache.config.php
  (:require [file.system :as fs]
            [log.log :as log]
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
  (log/debug :without-time "Reading app config by shelling out to php")
  (let [cmd (env-config-cmd magento-basedir)
        output (.execSync child-process cmd)
        config (js->clj (json/parse output) :keywordize-keys true)]
    (into {} config)))


(defn- unescape-php-var-on-win-os [php]
  (cond-> php (fs/win?) (string/replace #"\\\$" "$")))

(defn- list-components-cmd
  "Returns the PHP command to run to output a list of all Magento components of the given type.

  Currently the base path is removed from the beginning of the paths, only to be added
  again later in `list-component-dirs`. This doesn't make sense, except that I'm thinking
  about allowing a different Magento base dir path for PHP commands to be specified by
  the user. In that case the magento-basedir provided to this method would be a
  different one than the one added back on later.
  If I decide against this feature then I can remove this logic again and let PHP
  return complete paths directly again.
  Reference https://github.com/mage2tv/magento-cache-clean/issues/33#issuecomment-479791859"
  [magento-basedir type]
  (let [composer-autoload (str magento-basedir "vendor/autoload.php")]
    (when-not (fs/exists? composer-autoload)
      (throw (ex-info (str "Composer autoload.php not found: " composer-autoload) {})))
    (unescape-php-var-on-win-os
     (str "php -r "
          "\"require '" composer-autoload "'; "
          "\\$bp = strlen(dirname(dirname(realpath('" composer-autoload "')))) + 1; "
          "foreach ((new \\Magento\\Framework\\Component\\ComponentRegistrar)->getPaths('" type "') as \\$m) "
          "echo substr(\\$m, \\$bp).PHP_EOL;\""))))

(defn list-component-dirs [magento-basedir type]
  (log/debug :without-time (str "Listing " type "s by shelling out to php"))
  (let [magento-basedir (fs/add-trailing-slash magento-basedir)
        cmd (list-components-cmd magento-basedir type)
        output (.execSync child-process cmd)]
    (map #(str magento-basedir %) (string/split-lines output))))


(defn app-config-dir [magento-basedir]
  (str magento-basedir "app/etc"))

(defn watch-for-new-modules! [magento-basedir callback]
  (let [config-php-dir (app-config-dir magento-basedir)]
    (log/debug :without-time "Monitoring app/etc/config.php for new modules")
    (fs/watch config-php-dir (fn [file]
                               (when (or (= "config.php" (fs/basename file))
                                         (= "env.php" (fs/basename file)))
                                 (callback))))))
