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

(defn show-all-caches-disabled-notice []
  (when (mage/all-caches-disabled?)
    (log/notice :without-time "NOTE: All caches are currently disabled.")
    (log/notice :without-time "NOTE: Consider enabling all caches while running this utility.\n")))

(defn show-some-caches-disabled-notice []
  (when (mage/some-cache-disabled?)
    (log/notice :without-time "Consider enabling the currently disabled caches:")
    (log/notice :without-time (string/join ", " (map name (mage/disabled-caches))))))

(defn show-disabled-caches-notice []
  (if (mage/all-caches-disabled?)
    (show-all-caches-disabled-notice)
    (show-some-caches-disabled-notice)))

(defn controller? [file]
  (or (re-find #"/Controller/.+\.php$" file)
      (re-find #"\\Controller\\.+\.php$" file)))

(defn- contains-js-translation?
  "Return true if the given file potentially contains js translation code"
  [file]
  (when (and (fs/file? file)
             (or (string/ends-with? file ".phtml")
                 (string/ends-with? file ".html")
                 (string/ends-with? file ".js")))
    (let [contents (fs/slurp file)]
      (or (and (or (string/ends-with? file ".html") (string/ends-with? file ".phtml"))
               (or (string/includes? contents "i18n:")
                   (string/includes? contents ".mage.__(")
                   (string/includes? contents "$t(")))
          (and (string/ends-with? file ".html")
               (or (string/includes? contents "translate=")
                   (string/includes? contents "translate args=")))))))

(defn clean-cache-for-new-controller [file]
  (when (and (controller? file) (not (contains? @controllers file)))
    (cache/clean-cache-ids ["app_action_list"])
    (cache/clean-cache-types ["full_page"])
    (swap! controllers conj file)))

(defn- without-base-path [file]
  (subs file (count (mage/base-dir))))

(defn check-remove-generated-files-based-on-php! [php-file]
  (when (= ".php" (subs php-file (- (count php-file) 4)))
    (run! #(generated/remove-generated-files-based-on-php! % php-file) (mage/all-base-dirs))))

(defn check-remove-generated-extension-attributes-php! [file]
  (when (= "extension_attributes.xml" (fs/basename file))
    (run! generated/remove-generated-extension-attributes-php! (mage/all-base-dirs))))

(defn remove-generated-js-translation-json! []
  (log/info "Removing compiled frontend js-translation.json files")
  (run! fs/rm (static/js-translation-files (mage/base-dir) "frontend")))

(defn check-remove-generated-js-translation-json! [file]
  (when (or (string/ends-with? file ".csv")
            (contains-js-translation? file))
    (remove-generated-js-translation-json!)))

(defn remove-generated-files-based-on! [file]
  (check-remove-generated-files-based-on-php! file)
  (check-remove-generated-extension-attributes-php! file)
  (check-remove-generated-js-translation-json! file))

(defn compiled-requirejs-config? [file]
  (when (= "requirejs-config.js" (fs/basename file))
    (let [static-content-dirs (static/static-content-theme-locale-dirs (mage/base-dir) "frontend")
          static-theme-dirs (into #{} (map fs/realpath) static-content-dirs)]
      (contains? static-theme-dirs (fs/dirname file)))))

(defn removed-requirejs-config? [file]
  (and (not (fs/exists? file)) (compiled-requirejs-config? file)))

(defn process-changed-file? [file]
  (and (string? file)
       (not (re-find #"___jb_...___" file))
       (not (string/includes? file "/.git/"))
       (not (string/includes? file "\\.git\\"))
       (not (string/includes? file "/.mutagen-temporary"))
       (not (string/ends-with? file ".unison.tmp"))
       (not (string/ends-with? file "~"))
       (not (in-process? file))))

(defn file-changed [file]
  (when (process-changed-file? file)
    (set-in-process! file)
    (log/info "Processing" file)
    (when-let [types (seq (cache/magefile->cachetypes file))]
      (cache/clean-cache-types types))
    (when-let [ids (cache/magefile->cacheids file)]
      (cache/clean-cache-ids ids))
    (clean-cache-for-new-controller file)
    (remove-generated-files-based-on! file)
    (show-all-caches-disabled-notice)))

(defn module-controllers [module-dir]
  (filter #(re-find #"\.php$" %) (fs/file-tree (str module-dir "/Controller"))))

(defn watch-module [log-fn module-dir]
  (when (and (fs/exists? module-dir) (not (fs/watched? module-dir)))
    (fs/watch-recursive module-dir file-changed)
    (swap! controllers into (module-controllers module-dir))
    (log-fn module-dir)))

(defn watch-theme [theme-dir]
  (fs/watch-recursive theme-dir file-changed)
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

(defn static-file-changed [file]
  (when (process-changed-file? file)
    (set-in-process! file)
    (log/info "Processing" file)
    (when (removed-requirejs-config? file)
      (cache/clean-cache-types ["full_page"]))))

(defn watch-pub-static-frontend! []
  (let [dir (static/static-content-area-dir (mage/base-dir) "frontend")]
    ;; temporary workaround until branch "branch-switching" is merged
    (when (fs/dir? dir)
      (log/debug :without-time "Watching static files in" dir)
      (fs/watch-recursive dir static-file-changed))))

(defn watch-app-i18n! []
  (let [i18n-dir (str (mage/base-dir) "app/i18n/")]         ;; trailing slash is important to deref symlink
    (when (fs/exists? i18n-dir)                             ;; check with exists? instead of dir? to include symlinks
      (log/debug :without-time "Watching app/i18n/")
      (fs/watch-recursive i18n-dir file-changed))))

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
  (watch-pub-static-frontend!)
  (watch-app-i18n!)
  (watch-for-new-modules!)
  (show-disabled-caches-notice)
  (when (hotkeys/observe-keys! (mage/base-dir))
    (show-hotkeys))
  (log/notice :without-time "Watcher initialized (Ctrl-C to quit)"))
