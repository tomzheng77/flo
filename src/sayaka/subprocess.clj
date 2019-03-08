(ns sayaka.subprocess
  (:require
    [sayaka.constants :as c]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import (org.apache.commons.exec DefaultExecutor PumpStreamHandler ExecuteException)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (java.util Map)))

(defn- is-executable-file
  [file-path]
  (let [file (io/file file-path)]
    (and
      (.exists file)
      (.isFile file)
      (.canExecute file))))

(defn- is-readable-dir
  [file-path]
  (let [file (io/file file-path)]
    (and
      (.exists file)
      (.isDirectory file)
      (.canRead file))))

(defn- read-process [process-dir]
  {:pid (read-string (.getName process-dir))
   :exe (.getCanonicalPath (io/file process-dir "exe"))})

(defn process-report
  "reads all process information in /proc. for each process,
  it returns it's pid and executable location"
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
  completes or an error is thrown"
  ([cmd & args]
   (let [exec (first (find-executable cmd))
         #^"[Ljava.lang.String;" args-array (into-array String args)]
     (if-not exec
       (error "the command" cmd "is not found")
       (do
         (debug "run:" cmd "=>" exec args)
         (let [command (new org.apache.commons.exec.CommandLine exec)
               service (new DefaultExecutor)
               stdin (new ByteArrayInputStream (byte-array []))
               stdout (new ByteArrayOutputStream)
               stderr (new ByteArrayOutputStream)
               pump (new PumpStreamHandler stdout stderr stdin)
               ^Map env {"PATH"        "asdf"
                         "http_proxy"  ""
                         "https_proxy" ""}]
           (do
             (.addArguments command args-array)
             (.setStreamHandler service pump)
             (let [exit-value (try (.execute service command env) (catch ExecuteException e (.getExitValue e)))
                   stdout-bytes (.toByteArray stdout)
                   stderr-bytes (.toByteArray stderr)]
               {:exit   exit-value
                :stdout (slurp stdout-bytes)
                :stderr (slurp stderr-bytes)}))))))))

(defn send-notify
  "sends a notification to the user via the message bus.
  dunst needs to be installed first"
  ([title] (send-notify title ""))
  ([title message]
   (let [user-id (read-string (str/trim (:stdout (call "id" "-u" c/user))))]
     (call "sudo" "-u" c/user "DISPLAY=${display:1:-1}"
           (str "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/" user-id "/bus")
           "notify-send" title message))))
