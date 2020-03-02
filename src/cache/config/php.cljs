(ns cache.config.php
  (:require [file.system :as fs]
            [log.log :as log]
            [goog.json :as json]
            [clojure.string :as string]))

(defonce child-process (js/require "child_process"))

(defn- integration-test-base-dir? [dir]
  (string/includes? dir "dev/tests/integration/tmp/sandbox-"))

(defn- etc-env-php-file [base-dir]
  (let [dir (fs/add-trailing-slash base-dir)]
    (if (integration-test-base-dir? dir)
      (str dir "etc/env.php")
      (str dir "app/etc/env.php"))))

(defn- env-config-cmd [magento-basedir]
  (let [etc-env-php-file (etc-env-php-file magento-basedir)]
    (when-not (fs/exists? etc-env-php-file)
      (throw (ex-info (str "File env.php not found: " etc-env-php-file) {})))
    (str "php -r "
         "\"echo json_encode("
         "(require '" etc-env-php-file "') ?? []"
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

(defn *list-component-dirs [magento-basedir type]
  (log/debug :without-time (str "Listing " type "s by shelling out to php"))
  (let [magento-basedir (fs/add-trailing-slash magento-basedir)
        cmd (list-components-cmd magento-basedir type)
        output (.execSync child-process cmd)]
    (map #(str magento-basedir %) (string/split-lines output))))

(defn list-component-dirs [magento-basedir type]
  ;; PHP can fail hard. For example, composer might try to require an registration.php file
  ;; that doesn't exist any more, e.g. after switching git branches. To handle such cases gracefully,
  ;; the error is caught and an empty seq is returned.
  (try
    (*list-component-dirs magento-basedir type)
    (catch :default e
      (log/error (str "ERROR: failed shelling out to php for reading the " type " list."))
      ;; Cut off first line with failed CLI php command
      (let [lines (drop 1 (string/split-lines (or (.-message e) e)))]
        (log/info "ERROR Details:" (first lines))
        (log/debug "ERROR Details:" (string/join "\n" (rest lines))))
      '())))


(defn app-config-dir [magento-basedir]
  (str magento-basedir "app/etc"))

(defn watch-for-new-modules! [magento-basedir callback]
  (let [config-php-dir (app-config-dir magento-basedir)]
    (log/debug :without-time "Monitoring app/etc/config.php for new modules")
    (fs/watch config-php-dir (fn [file]
                               (when (or (= "config.php" (fs/basename file))
                                         (= "env.php" (fs/basename file)))
                                 (callback))))))

(defn mtime [base-dir]
  (fs/mtime (etc-env-php-file base-dir)))