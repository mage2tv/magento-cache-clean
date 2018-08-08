(ns magento.watcher
  (:require [cache.filestorage :as storage]
            [cache.log :as log]
            [cache.cache :as cache]
            [file.system :as fs]
            [clojure.string :as string]))

(defonce child-process (js/require "child_process"))

(defonce watches (atom {}))

(defonce in-process-files (atom {}))

(defn list-modules-cmd [magento-basedir]
  (let [composer-autoload (str magento-basedir "vendor/autoload.php")]
    (when-not (fs/exists? composer-autoload)
      (throw (ex-info (str "Composer autoload.php not found: " composer-autoload) {})))
    (str "php -r "
         "'require \"" composer-autoload "\"; "
         "foreach ((new \\Magento\\Framework\\Component\\ComponentRegistrar)->getPaths(\"module\") as $m) "
         "echo $m.PHP_EOL;'")))

(defn module-dirs [magento-basedir]
  (let [cmd (list-modules-cmd magento-basedir)
        output (.execSync child-process cmd)]
    (string/split-lines output)))

(defn timestamp []
  (.getTime (js/Date.)))

(defn set-in-process! [file]
  (swap! in-process-files assoc file (timestamp)))

(defn set-not-in-process! [file]
  (swap! in-process-files dissoc file) nil)

(defn in-process? [file]
  (when-let [time (get @in-process-files file)]
    (if (< (+ time 1000) (timestamp))
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
                        "/view/frontend/ui_component/"
                        "/view/frontend/templates/"
                        "/view/adminhtml/layout/"
                        "/view/adminhtml/ui_component/"
                        "/view/adminhtml/templates/"
                        "/view/base/layout/"
                        "/view/base/ui_component/"
                        "/view/base/templates/"
                        "/i18n/"]]
    (reduce (fn [acc dir]
              (into acc (fs/dir-tree dir))) [] (map #(str module-dir %) dirs-in-module))))

(defn watch-module [module-dir]
  (let [dirs (dirs-in-modules-to-watch module-dir)]
    (run! (fn [dir]
            #_(log/debug "Watching dir" dir)
            (swap! watches assoc dir (fs/watch dir file-changed))) dirs))
  (log/debug "Watching module" (fs/basename module-dir)))

(defn stop []
  (run! (fn [dir]
          (try
            (swap! watches (fn [watches]
                             (when-let [watch (get watches dir)]
                               (prn watch)
                               (.close watch)
                               (dissoc watches dir))))
            (log/debug "Stopped watching" dir)
            (catch :default e))) (keys @watches))
  (log/always "Stopped watching"))

(defn start []
  (let [magento-basedir (storage/base-dir)]
    (when (seq @watches) (stop))

    (run! watch-module (module-dirs magento-basedir))
    ;; TODO: watch themes
    (log/notice "Watcher initialized (Ctrl-C to quit)")))
