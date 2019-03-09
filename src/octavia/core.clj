(ns octavia.core
  (:require
    [octavia.constants :as c]
    [octavia.subprocess :as s]
    [octavia.state :as st]
    [octavia.proxy :as proxy]
    [octavia.http-server :as http]
    [octavia.restrictions :as r]
    [octavia.utils :as u]
    [criterium.core :as crit]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]
    [taoensso.timbre.appenders.core :as appenders]
    [clojure.java.io :as io])
  (:import (org.apache.commons.lang3 SystemUtils)))

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname c/primary-log})}})

(defn flat-list [& args]
  (filter
    (complement nil?)
    (flatten
      (list args))))

(defn check-environment
  "checks the system to determine whether it is suitable to
  run this program. returns a list of error messages."
  []
  (flat-list
    ;(if-not (= (System/getenv "USER") "root") "user is not root")
    (if-not SystemUtils/IS_OS_LINUX "environment is not linux")
    (for [cmd c/commands]
      (if (empty? (s/find-executable cmd))
        (str "executable '" cmd "' cannot be located")))))

(defn octavia-client [])
(defn octavia-server []
  (info "octavia starting")
  (let [errors (check-environment)]
    (if (not-empty errors)
      (do
        (doseq [e errors] (fatal e))
        (System/exit 1))))
  (http/start-server)
  (let [state (st/read-state)]
    (proxy/start-server (st/proxy-settings state)))
  (s/call "iptables" "iptables" "-w" "10" "-A" "OUTPUT" "-p" "tcp" "-m" "owner" "--uid-owner" c/user "--dport" "80" "-j" "REJECT")
  (s/call "iptables" "iptables" "-w" "10" "-A" "OUTPUT" "-p" "tcp" "-m" "owner" "--uid-owner" c/user "--dport" "443" "-j" "REJECT")
  (info "octavia started"))

(defn octavia-orbit [])

(defn -main
  ([] octavia-client)
  ([mode]
   (case mode
     "--server" (octavia-server)
     "--client" (octavia-client)
     "--orbit" (octavia-orbit))))
