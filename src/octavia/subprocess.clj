(ns octavia.subprocess
  (:require
    [octavia.constants :as c]
    [octavia.utils :as u]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.java.shell :as sh]))

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
       (filter u/is-readable-dir)
       (map read-process)))

(defn find-executable
  [file-name]
  (->> c/global-paths
       (map (fn [path-item] (str path-item c/file-separator file-name)))
       (filter u/is-executable-file)))

(defn call
  "runs a command with the given arguments. waits until the execution
  completes or an error is thrown. returns :exit, :out and :err."
  ([cmd & args]
   (let [exec (first (find-executable cmd))]
     (if-not exec
       (error "the command" cmd "is not found")
       (do
         (debug "run:" cmd "=>" exec args)
         (apply sh/sh
                (concat (cons cmd args) [:env
                                         {"PATH"        c/global-path
                                          "http_proxy"  ""
                                          "https_proxy" ""}])))))))

(defn send-notify
  "sends a notification to the user via the message bus.
  dunst needs to be installed first"
  ([title] (send-notify title ""))
  ([title message]
   (let [user-id (read-string (str/trim (:out (call "id" "-u" c/user))))]
     (call "sudo" "-u" c/user "DISPLAY=${display:1:-1}"
           (str "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/" user-id "/bus")
           "notify-send" title message))))
