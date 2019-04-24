(ns magento.watcher
  (:require [log.log :as log]
            [clojure.string :as string]
            [magento.app :as mage]
            [cache.cache :as cache]
            [file.system :as fs]
            [magento.generated-code :as generated]
            [cache.hotkeys :as hotkeys]))

(defonce in-process-files (atom {}))

(defonce controllers (atom #{}))

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
  (or (re-find #"/Controller/.+\.php$" file)
      (re-find #"\\Controller\\.+\.php$" file)))

(defn clean-cache-for-new-controller [file]
  (when (and (controller? file) (not (contains? @controllers file)))
    (cache/clean-cache-ids ["app_action_list"])
    (cache/clean-cache-types ["full_page"])
    (swap! controllers conj file)))

(defn- without-base-path [file]
  (subs file (count (mage/base-dir))))

(defn remove-generated-files-based-on! [file]
  (when (= ".php" (subs file (- (count file) 4)))
    (let [files (generated/php-file->generated-code-files file)]
      (when (seq files)
        (apply log/info "Removing generated code"
               (interpose ", " (map without-base-path files)))
        (run! fs/rm files)))))

(defn file-changed [file]
  (when (and (string? file)
             (not (re-find #"___jb_...___" file))
             (not (string/includes? file "/.git/"))
             (not (string/includes? file "\\.git\\"))
             (not (in-process? file)))
    (set-in-process! file)
    (log/info "Processing" file)
    (when-let [types (seq (cache/magefile->cachetypes file))]
      (cache/clean-cache-types types))
    (when-let [ids (cache/magefile->cacheids file)]
      (cache/clean-cache-ids ids))
    (clean-cache-for-new-controller file)
    (remove-generated-files-based-on! file)))

(defn module-controllers [module-dir]
  (filter #(re-find #"\.php$" %) (fs/file-tree (str module-dir "/Controller"))))

(defn watch-module [module-dir]
  (when (and (fs/exists? module-dir) (not (fs/watched? module-dir)))
    (fs/watch-recursive module-dir #(file-changed %))
    (swap! controllers into (module-controllers module-dir))
    (log/debug "Watching module" (fs/basename module-dir))))

(defn watch-theme [theme-dir]
  (fs/watch-recursive theme-dir #(file-changed %))
  (log/debug "Watching theme" (fs/basename theme-dir)))

(defn watch-all-modules! []
  (run! watch-module (mage/module-dirs)))

(defn watch-new-modules! []
  (log/debug "Checking for new modules...")
  (watch-all-modules!))

(defn watch-for-new-modules! []
  (let [config-php-dir (str (mage/base-dir) "app/etc")]
    (log/debug "Monitoring app/etc/config.php for new modules")
    (fs/watch config-php-dir (fn [file]
                               (when (= "config.php" (fs/basename file))
                                 (watch-new-modules!)
                                 (cache/clean-cache-types ["config"]))))))


(defn stop []
  (fs/stop-all-watches)
  (log/always "Stopped watching"))

(defn show-hotkeys []
  (log/notice "Hot-keys for manual cache cleaning:")
  (log/notice "[c]onfig [b]lock_html [l]ayout [t]ranslate [f]ull_page [v]iew [a]ll\n")
  (log/notice "Hot-key for cleaning all generated code: [G]")
  (log/notice "Hot-keys for cleaning static content areas: [F]rontend [A]dminhtml\n"))

(defn start []
  (watch-all-modules!)
  (run! watch-theme (mage/theme-dirs))
  (watch-for-new-modules!)
  (when (hotkeys/observe-keys!)
    (show-hotkeys))
  (log/notice "Watcher initialized (Ctrl-C to quit)"))
