(ns limiter.subprocess
  (:require
    [limiter.constants :as c]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]
    [clojure.java.io :as io]
    [clojure.java.shell :as sh]))

(defn- read-process [process-dir]
  {:pid (read-string (.getName process-dir))
   :exe (.getCanonicalPath (io/file process-dir "exe"))})

(defn- is-readable-dir
  [file-path]
  (let [file (io/file file-path)]
    (and
      (.exists file)
      (.isDirectory file)
      (.canRead file))))

(defn- is-executable-file
  [file-path]
  (let [file (io/file file-path)]
    (and
      (.exists file)
      (.isFile file)
      (.canExecute file))))

(defn process-report
  "reads all process information in /proc. for each process,
  it returns it's pid and executable location
  [{:pid 4299 :exe \"/opt/google/chrome/chrome\"}, ...]"
  []
  (->> c/processes-dir
       (io/file)
       (.listFiles)
       (set)
       (filter is-readable-dir)
       (map read-process)))

(defn find-executable
  [file-name]
  (->> c/global-paths
       (map (fn [path-item] (str path-item c/file-separator file-name)))
       (filter is-executable-file)))

(defn call
  "runs a command with the given arguments. waits until the execution
  completes or an error is thrown. returns :exit, :out and :err."
  ([cmd & args]
   (let [exec (first (find-executable cmd))]
     (if-not exec
       (error "the command" cmd "is not found")
       (do
         (debug "run:" cmd "=>" exec args)
         (let [result (apply sh/sh
                             (concat [exec] args [:env]
                                     [{"PATH" c/global-path "http_proxy" "" "https_proxy" ""}]))]
           (when-not (= 0 (:exit result))
             (error cmd args result))
           result))))))
