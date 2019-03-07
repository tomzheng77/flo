(ns sayaka.core
  (:require
    [sayaka.constants :as c]
    [sayaka.subprocess :as s]
    [sayaka.proxy :as proxy]
    [sayaka.http-server :as http]
    [sayaka.restrictions :as r]
    [sayaka.utils :as u]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]
    [taoensso.timbre.appenders.core :as appenders]
    [clojure.java.io :as io])
  (:import (org.apache.commons.lang3 SystemUtils)))

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname c/primary-log})}})

(def is-root (= (System/getenv "USER") "root"))
(def is-linux SystemUtils/IS_OS_LINUX)

(defn check-environment
  []
  (try
    (do
      (assert is-root "user is not root")
      (assert is-linux "environment is not linux")
      (doseq [cmd c/commands]
        (assert
          (not-empty (s/find-executable cmd))
          (str "executable '" cmd "' cannot be located"))))
    (catch Exception e
      (do (fatal e)
          (System/exit 1)))))

(defn sayaka-client [])
(defn sayaka-server []
  (info "S.A.Y.A.K.A starting")
  (check-environment)
  (proxy/start-server)
  (http/start-server)
  (s/call "iptables" "iptables" "-w" "10" "-A" "OUTPUT" "-p" "tcp" "-m" "owner" "--uid-owner" c/user "--dport" "80" "-j" "REJECT")
  (s/call "iptables" "iptables" "-w" "10" "-A" "OUTPUT" "-p" "tcp" "-m" "owner" "--uid-owner" c/user "--dport" "443" "-j" "REJECT")
  (info "S.A.Y.A.K.A started"))

(defn sayaka-orbit [])

(defn -main
  ([] sayaka-client)
  ([mode]
   (case mode
     "--server" (sayaka-server)
     "--client" (sayaka-client)
     "--orbit" (sayaka-orbit))))
