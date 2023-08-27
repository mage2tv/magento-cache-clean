(ns magento.watcher
  (:require [log.log :as log]
            [clojure.string :as string]
            [magento.app :as mage]
            [cache.cache :as cache]
            [cache.config :as cache-config]
            [file.system :as fs]
            [magento.generated-code :as generated]
            [magento.static-content :as static]
            [cache.hotkeys :as hotkeys]
            [magento.service-contract :as service-contract]
            [cljs.core.async :refer [go <! timeout]]))

(defonce in-process-files (atom {}))

(defonce controllers (atom #{}))


(defonce changed-file-queue (atom #{}))

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

(defn clean-cache-for-service-interface [file]
  (cache/clean-cache-ids (service-contract/service-cache-ids file)))

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

(defn- now []
  (.getTime (js/Date.)))

(def cache-clean-guard-period (atom 5000))

(defn set-cache-clean-guard-period! [ms]
  (log/notice "Set cache clean guard period to" (str ms "ms"))
  (reset! cache-clean-guard-period ms)) ;; ms

(let [last-cleaned-map (atom {})]

  (defn- last-cleaned [cache-type-or-id]
    (get @last-cleaned-map cache-type-or-id))

  (defn- update-last-cleaned [cache-type-or-id]
    (swap! last-cleaned-map assoc cache-type-or-id (now))))

(defn- may-clean? [cache-type-or-id]
  (let [prev (last-cleaned cache-type-or-id)
        t (now)]
    (if (or (nil? prev) (< 0 (- t prev @cache-clean-guard-period)))
      (do (update-last-cleaned cache-type-or-id)
          #_(log/notice "OK to clean" cache-type-or-id "since")
          true)
      (do #_(log/notice "WAIT grace period" cache-type-or-id (str (- t prev @cache-clean-guard-period) "ms"))
        false))))

(defn- clean-cache-types [types]
  (if (seq types)
    (let [types (filter may-clean? types)]
      (when (seq types) (cache/clean-cache-types types)))
    (when (may-clean? :all)
      (cache/clean-cache-types types))))

(defn- clean-cache-ids [ids]
  (let [ids (filter may-clean? ids)]
    (when (seq ids)
      (cache/clean-cache-ids ids))))


(defn file-changed [file]
  (when (process-changed-file? file)
    (set-in-process! file)
    (log/info "Processing" file)
    (when-let [types (seq (cache/magefile->cachetypes file))]
      (clean-cache-types types))
    (when-let [ids (cache/magefile->cacheids file)]
      (clean-cache-ids ids))
    (clean-cache-for-new-controller file)
    (clean-cache-for-service-interface file)
    (remove-generated-files-based-on! file)
    (show-all-caches-disabled-notice)))

(defn- queue-file! [file]
  (swap! changed-file-queue conj file))

(defn shift-file! []
  (let [file (first @changed-file-queue)]
    (swap! changed-file-queue disj file)
    file))

(defn- start-file-processing []
  (go (while true
        (do
          (when-let [file (shift-file!)]
            (file-changed file))
          (<! (timeout 5)))))) ;; allow keyboard events to be processed

(defn module-controllers [module-dir]
  (filter #(re-find #"\.php$" %) (fs/file-tree (str module-dir "/Controller"))))

(defn watch-module [log-fn module-dir]
  (when (and (fs/exists? module-dir) (not (fs/watched? module-dir)))
    (fs/watch-recursive module-dir queue-file!)
    (swap! controllers into (module-controllers module-dir))
    (log-fn module-dir)))

(defn watch-theme [theme-dir]
  (fs/watch-recursive theme-dir queue-file!)
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
      (fs/watch-recursive i18n-dir queue-file!))))

(defn stop []
  (fs/stop-all-watches)
  (log/always "Stopped watching"))

(defn show-hotkeys []
  (log/notice :without-time "Hot-keys for manual cache cleaning:")
  (log/notice :without-time "[c]onfig [b]lock_html [l]ayout [t]ranslate [f]ull_page [v]iew [a]ll\n")
  (log/notice :without-time "Clean generated code: [G]")
  (log/notice :without-time "Clean integration test sandboxes: [I]")
  (log/notice :without-time "Clean static content areas: [F]rontend [A]dminhtml\n"))

(defn start []
  (watch-all-modules! log-watching-module)
  (run! watch-theme (mage/theme-dirs))
  (watch-pub-static-frontend!)
  (watch-app-i18n!)
  (watch-for-new-modules!)
  (show-disabled-caches-notice)
  (when (hotkeys/observe-keys! (mage/base-dir))
    (show-hotkeys))
  (start-file-processing)
  (log/notice :without-time "Watcher initialized (Ctrl-C to quit)"))
