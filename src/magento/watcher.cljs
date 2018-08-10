(ns magento.watcher
  (:require [cache.filestorage :as storage]
            [log.log :as log]
            [cache.cache :as cache]
            [file.system :as fs]
            [clojure.string :as string]))

(defonce child-process (js/require "child_process"))

(defonce in-process-files (atom {}))

(defonce controllers (atom #{}))

(defn list-components-cmd [magento-basedir type]
  (let [composer-autoload (str magento-basedir "vendor/autoload.php")]
    (when-not (fs/exists? composer-autoload)
      (throw (ex-info (str "Composer autoload.php not found: " composer-autoload) {})))
    (str "php -r "
         "'require \"" composer-autoload "\"; "
         "foreach ((new \\Magento\\Framework\\Component\\ComponentRegistrar)->getPaths(\"" type "\") as $m) "
         "echo $m.PHP_EOL;'")))

(defn list-component-dirs [magento-basedir type]
  (let [cmd (list-components-cmd magento-basedir type)
        output (.execSync child-process cmd)]
    (string/split-lines output)))

(defn module-dirs [magento-basedir]
  (list-component-dirs magento-basedir "module"))

(defn theme-dirs [magento-basedir]
  (list-component-dirs magento-basedir "theme"))

(defn timestamp []
  (.getTime (js/Date.)))

(defn set-in-process! [file]
  (swap! in-process-files assoc file (timestamp)))

(defn set-not-in-process! [file]
  (swap! in-process-files dissoc file) nil)

(defn in-process? [file]
  (when-let [time (get @in-process-files file)]
    (if (< (+ time 500) (timestamp))
      (set-not-in-process! file)
      true)))

(defn controller? [file]
  (re-find #"/Controller/.+\.php$" file))

(defn clean-cache-for-new-controller [file]
  (when (and (controller? file) (not (contains? @controllers file)))
    (cache/clean-cache-types ["config"])
    (swap! controllers conj file)))

(defn file-changed [file]
  (when (and (fs/exists? file)
             (not (re-find #"___jb_...___" file))
             (not (in-process? file)))
    (set-in-process! file)
    (log/debug "Processing" file)
    (when-let [types (seq (cache/magefile->cachetypes file))]
      (cache/clean-cache-types types))
    (clean-cache-for-new-controller file)))

(defn module-controllers [module-dir]
  (filter #(re-find #"\.php$" %) (fs/file-tree (str module-dir "/Controller"))))

(defn watch-module [module-dir]
  (when (fs/exists? module-dir)
    (fs/watch-recursive module-dir #(file-changed %))
    (swap! controllers into (module-controllers module-dir))
    (log/debug "Watching module" (fs/basename module-dir))))

(defn watch-theme [theme-dir]
  (fs/watch-recursive theme-dir #(file-changed %))
  (log/debug "Watching theme" (fs/basename theme-dir)))

(defn stop []
  (fs/stop-all-watches)
  (log/always "Stopped watching"))

(defn start []
  (let [magento-basedir (storage/base-dir)]
    (run! watch-module (module-dirs magento-basedir))
    (run! watch-theme (theme-dirs magento-basedir))
    (log/notice "Watcher initialized (Ctrl-C to quit)")))
