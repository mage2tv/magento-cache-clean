(ns magento.watcher
  (:require [cache.filestorage :as storage]
            [log.log :as log]
            [cache.cache :as cache]
            [file.system :as fs]
            [clojure.string :as string]))

(defonce child-process (js/require "child_process"))

(defonce watches (atom {}))

(defonce in-process-files (atom {}))

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

(defn file-changed [file]
  (when (and (fs/exists? file)
             (not (re-find #"___jb_...___" file))
             (not (in-process? file)))
    (set-in-process! file)
    (log/debug "Processing" file)
    (when-let [types (seq (cache/magefile->cachetypes file))]
      (cache/clean-cache-types types))))

(defn dirs-in-modules-to-watch [module-dir]
  (let [dirs-in-module ["/etc/"
                        "/view/frontend/layout/"
                        "/view/frontend/page_layout/"
                        "/view/frontend/ui_component/"
                        "/view/frontend/templates/"
                        "/view/adminhtml/layout/"
                        "/view/adminhtml/page_layout/"
                        "/view/adminhtml/ui_component/"
                        "/view/adminhtml/templates/"
                        "/view/base/layout/"
                        "/view/base/page_layout/"
                        "/view/base/ui_component/"
                        "/view/base/templates/"
                        "/i18n/"]]
    (filter fs/exists? (map #(str module-dir %) dirs-in-module))))

(defn watch-module [module-dir]
  (let [dirs (dirs-in-modules-to-watch module-dir)]
    (run! (fn [dir]
            (let [watch (fs/watch-recursive dir #(file-changed %))]
              (swap! watches assoc dir watch))) dirs))
  (log/debug "Watching module" (fs/basename module-dir)))

(defn watch-theme [theme-dir]
  (let [watch (fs/watch-recursive theme-dir #(file-changed %))]
    (swap! watches assoc theme-dir watch))
  (log/debug "Watching theme" (fs/basename theme-dir)))

(defn stop []
  (run! (fn [dir]
          (try
            (swap! watches (fn [watches]
                             (when-let [watch (get watches dir)]
                               (.close watch)
                               (dissoc watches dir))))
            (log/debug "Stopped watching" dir)
            (catch :default e))) (keys @watches))
  (log/always "Stopped watching"))

(defn start []
  (let [magento-basedir (storage/base-dir)]
    (when (seq @watches) (stop))

    (run! watch-module (module-dirs magento-basedir))
    (run! watch-theme (theme-dirs magento-basedir))
    (log/notice "Watcher initialized (Ctrl-C to quit)")))
