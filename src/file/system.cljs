(ns file.system
  (:refer-clojure :exclude [exists?]))

(defonce fs (js/require "fs"))
(defonce path (js/require "path"))
(defonce process (js/require "process"))

(defn- win? []
  (= "win32" (.-platform process)))

(defn- mac? []
  (= "darwin" (.-platform process)))

(defn- trailing-slash [s]
  (if (= "/" (subs s (- (count s) 1))) s (str s "/")))

(defn exists? [file]
  (.existsSync fs file))

(defn dir? [s]
  (and (exists? s) (.. fs (lstatSync s) isDirectory)))

(defn symlink? [s]
  (.. fs (lstatSync s) isSymbolicLink))

(defn realpath [file]
  (if (exists? file)
    (.realpathSync fs file)
    file))

(defn basename [file]
  (.basename path file))

(defn dirname [file]
  (.dirname path file))

(defn cwd []
  (.cwd js/process))

(defn slurp [file]
  (str (.readFileSync fs file)))

(defn head
  "Return the first n bytes of the given file (default 1024 bytes)."
  ([file] (head file 1024))
  ([file bytes]
   (when (exists? file)
     (let [buffer (.alloc js/Buffer bytes)
           descriptor (.openSync fs file "r")]
       (.readSync fs descriptor buffer 0 bytes 0)
       (.closeSync fs descriptor)
       (str buffer)))))

(defn rm [file]
  (.unlinkSync fs file))

(defn rmdir
  "Remove the given directory if it is empty."
  [dir]
  (.rmdirSync fs dir))

(defn dir-tree
  "Return a list of all directories within and including the given dir"
  [dir]
  (if (dir? dir)
    (let [xfn (comp (map #(str (trailing-slash dir) %)) (filter dir?))
          dirs (into [] xfn (.readdirSync fs dir))]
      (reduce (fn [acc dir]
                (into acc (dir-tree dir))) [dir] dirs))
    []))

(defn watch
  ([dir-or-file callback] (watch dir-or-file #js {} callback))
  ([dir-or-file options callback]
   (let [prefix (trailing-slash (if (dir? dir-or-file)
                                  dir-or-file
                                  (dirname dir-or-file)))
         callback-wrapper (fn [event-type filename]
                            (when filename
                              (callback (str prefix filename))))]
     (.watch fs dir-or-file options callback-wrapper))))

(defn- recursive-watch-supported? []
  (or (mac?) (win?)))

(defn watch-recursive [dir callback]
  (if (not (dir? dir))
    (watch dir callback)
    (if (recursive-watch-supported?)
      (watch dir #js {:recursive true} callback)
      (let [watches (doall (map #(watch % callback) (dir-tree dir)))]
        #js {:close #(run! (fn [watch] (.close watch)) watches)}))))

(declare rmdir-recursive)

(defn rm-contents
  "Remove everything inside the given directory, but keep the directory itself."
  [dir]
  (when (not (dir? dir))
    (throw (ex-info (str "Error: \"" dir "\" is not a directory") {:dir dir})))
  (let [items (map #(str (trailing-slash dir) %) (.readdirSync fs dir))]
    (run! rm (filter (complement dir?) items))
    (run! rmdir-recursive (filter dir? items))))

(defn rmdir-recursive
  "Remove the given directory and it's contents."
  [dir]
  (rm-contents dir)
  (rmdir dir))
