(ns magento.watcher
  (:require [log.log :as log]
            [clojure.string :as string]
            [magento.app :as mage]
            [cache.cache :as cache]
            [cache.config :as cache-config]
            [file.system :as fs]
            [magento.generated-code :as generated]
            [magento.static-content :as static]
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

(defn remove-generated-files-based-on-php! [php-file]
  (when (= ".php" (subs php-file (- (count php-file) 4)))
    (let [files (generated/php-file->generated-code-files php-file)]
      (when (seq files)
        (log/info "Removing generated code"
               (apply str (interpose ", " (map without-base-path files))))
        (run! fs/rm files)))))

(defn remove-generated-extension-attributes [file]
  (when (= "extension_attributes.xml" (fs/basename file))
    (let [files (generated/generated-extension-attribute-classes)]
      (when (seq files)
        (log/info "Removing generated extension attributes classes"
               (apply str (interpose ", " (map without-base-path files))))
        (run! fs/rm files)))))

(defn remove-generated-js-translation-json! [file]
  (when (or (string/ends-with? file ".csv"))
    (log/info "Removing compiled frontend js-translation.json files.")
    (run! fs/rm (static/js-translation-files "frontend"))))

(defn remove-generated-files-based-on! [file]
  (remove-generated-files-based-on-php! file)
  (remove-generated-extension-attributes file)
  (remove-generated-js-translation-json! file))

(defn file-changed [file]
  (when (and (string? file)
             (not (re-find #"___jb_...___" file))
             (not (string/includes? file "/.git/"))
             (not (string/includes? file "\\.git\\"))
             (not (string/includes? file "/.mutagen-temporary"))
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

(defn watch-module [log-fn module-dir]
  (when (and (fs/exists? module-dir) (not (fs/watched? module-dir)))
    (fs/watch-recursive module-dir #(file-changed %))
    (swap! controllers into (module-controllers module-dir))
    (log-fn module-dir)))

(defn watch-theme [theme-dir]
  (fs/watch-recursive theme-dir #(file-changed %))
  (log/debug :without-time "Watching theme" (fs/basename theme-dir)))

(defn pretty-module-name [module-dir]
  (let [module-parent-dir-name (fs/basename (fs/dirname module-dir))
        module-dir-name (fs/basename module-dir)]
    (str module-parent-dir-name "/" module-dir-name)))

(defn log-watching-new-module [module-dir]
  (log/notice "Watching new module" (pretty-module-name module-dir)))

(defn log-watching-module [module-dir]
  (log/debug :without-time "Watching module" (pretty-module-name module-dir)))

(defn watch-all-modules! [log-fn]
  (run! #(watch-module log-fn %) (mage/module-dirs)))

(defn watch-new-modules! []
  (log/debug :without-time "Checking for new modules...")
  (watch-all-modules! log-watching-new-module))

(defn watch-for-new-modules! []
  (cache-config/watch-for-new-modules!
   (mage/base-dir)
   (fn []
     (watch-new-modules!)
     (cache/clean-cache-types ["config"]))))

(defn stop []
  (fs/stop-all-watches)
  (log/always "Stopped watching"))

(defn show-hotkeys []
  (log/notice :without-time "Hot-keys for manual cache cleaning:")
  (log/notice :without-time "[c]onfig [b]lock_html [l]ayout [t]ranslate [f]ull_page [v]iew [a]ll\n")
  (log/notice :without-time "Hot-key for cleaning all generated code: [G]")
  (log/notice :without-time "Hot-keys for cleaning static content areas: [F]rontend [A]dminhtml\n"))

(defn start []
  (watch-all-modules! log-watching-module)
  (run! watch-theme (mage/theme-dirs))
  (watch-for-new-modules!)
  (when (hotkeys/observe-keys!)
    (show-hotkeys))
  (log/notice :without-time "Watcher initialized (Ctrl-C to quit)"))
