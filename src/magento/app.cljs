(ns magento.app
  (:require [file.system :as fs]
            [cache.storage :as storage]
            [log.log :as log]
            [clojure.string :as string]
            [goog.json :as json]))

(defonce child-process (js/require "child_process"))

(defonce magento-basedir (atom "./"))

(defn set-base-dir! [dir]
  (reset! magento-basedir dir))

(defn base-dir []
  (fs/add-trailing-slash @magento-basedir))

(defn app-config-dir []
  (str (fs/realpath (base-dir)) "/app/etc/"))

(defn- unescape-php-var-on-win-os [php]
  (cond-> php (fs/win?) (string/replace #"\\\$" "$")))

(defn- env-config-cmd []
  (let [app-etc-env (str (base-dir) "app/etc/env.php")]
    (when-not (fs/exists? app-etc-env)
      (throw (ex-info (str "File app/etc/env.php not found: " app-etc-env) {})))
    (str "php -r "
         "\"echo json_encode("
         "(require '" app-etc-env "') ?? []"
         ");\"")))

(def read-app-config
  (memoize
   (fn []
     (let [cmd (env-config-cmd)
           output (.execSync child-process cmd)
           config (js->clj (json/parse output) :keywordize-keys true)]
       (into {} config)))))

(defn default-cache-id-prefix []
  (let [path (str (fs/realpath (base-dir)) "/app/etc/")
        id-prefix (str (subs (storage/md5 path) 0 3) "_")]
    (log/always "Calculated cache ID prefix" id-prefix "from" path)
    id-prefix))

(defn default-cache-dir [cache-type]
  (let [dir (if (= :page_cache cache-type) "page_cache" "cache")]
    (str (base-dir) "var/" dir)))

(defn read-cache-config []
  (let [config (read-app-config)]
    (get-in config [:cache :frontend])))

(defn file-cache-backend? [config]
  (or (not (:backend config))
      (= :backend "Cm_Cache_Backend_File")))

(defn missing-cache-dir? [config]
  (and (file-cache-backend? config) (not (:cache_dir config))))

(defn add-default-config-values [config cache-type]
  (cond-> config
    (file-cache-backend? config) (assoc :backend "Cm_Cache_Backend_File")
    (missing-cache-dir? config) (assoc :cache_dir (default-cache-dir cache-type))
    (not (:id_prefix config)) (assoc :id_prefix (default-cache-id-prefix))))

(defn cache-config
  "Given the cache type :default or :page_cache returns the configuration"
  [cache-type]
  (let [config (get (read-cache-config) cache-type {})]
    (add-default-config-values config cache-type)))

(defn varnish-hosts-config []
  (let [config (read-app-config)]
    (get config :http_cache_hosts)))

(defn- list-components-cmd [magento-basedir type]
  (let [composer-autoload (str magento-basedir "vendor/autoload.php")]
    (when-not (fs/exists? composer-autoload)
      (throw (ex-info (str "Composer autoload.php not found: " composer-autoload) {})))
    (unescape-php-var-on-win-os
     (str "php -r "
          "\"require '" composer-autoload "'; "
          "foreach ((new \\Magento\\Framework\\Component\\ComponentRegistrar)->getPaths('" type "') as \\$m) "
          "echo \\$m.PHP_EOL;\""))))

(defn- list-component-dirs [magento-basedir type]
  (let [cmd (list-components-cmd magento-basedir type)
        output (.execSync child-process cmd)]
    (string/split-lines output)))

(defn module-dirs []
  (list-component-dirs (base-dir) "module"))

(defn theme-dirs []
  (list-component-dirs (base-dir) "theme"))
