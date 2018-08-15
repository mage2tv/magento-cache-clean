(ns magento.app
  (:require [file.system :as fs]
            [clojure.string :as string]
            [goog.json :as json]))

(defonce child-process (js/require "child_process"))

(defonce magento-basedir (atom "./"))

(defn set-base-dir! [dir]
  (reset! magento-basedir dir))

(defn base-dir []
  (fs/add-trailing-slash @magento-basedir))

(defn- cache-config-cmd []
  (let [app-etc-env (str (base-dir) "app/etc/env.php")]
    (when-not (fs/exists? app-etc-env)
      (throw (ex-info (str "File app/etc/env.php not found: " app-etc-env) {})))
    (str "php -r "
         "'echo json_encode("
         "(require \"" app-etc-env "\")"
         "[\"cache\"][\"frontend\"] ?? []"
         ");'")))

(defn default-cache-config []
  {:page_cache {:backend "Cm_Cache_Backend_File"
                :cache_dir (str (base-dir) "var/page_cache")}
   :default {:backend "Cm_Cache_Backend_File"
             :cache_dir (str (base-dir) "var/cache")}})

(def read-cache-config
  (memoize
   (fn []
     (let [cmd (cache-config-cmd)
           output (.execSync child-process cmd)
           config (js->clj (json/parse output) :keywordize-keys true)]
       (into {} config)))))

(defn cache-config [cache-type]
  (let [config (read-cache-config)]
    (or (get config cache-type)
        (get (default-cache-config) cache-type)
        (get (default-cache-config) :default))))

(defn- list-components-cmd [magento-basedir type]
  (let [composer-autoload (str magento-basedir "vendor/autoload.php")]
    (when-not (fs/exists? composer-autoload)
      (throw (ex-info (str "Composer autoload.php not found: " composer-autoload) {})))
    (str "php -r "
         "'require \"" composer-autoload "\"; "
         "foreach ((new \\Magento\\Framework\\Component\\ComponentRegistrar)->getPaths(\"" type "\") as $m) "
         "echo $m.PHP_EOL;'")))

(defn- list-component-dirs [magento-basedir type]
  (let [cmd (list-components-cmd magento-basedir type)
        output (.execSync child-process cmd)]
    (string/split-lines output)))

(defn module-dirs []
  (list-component-dirs (base-dir) "module"))

(defn theme-dirs []
  (list-component-dirs (base-dir) "theme"))
