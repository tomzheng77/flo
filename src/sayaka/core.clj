(ns sayaka.core
  (:require
    [sayaka.constants :as c]
    [sayaka.subprocess :as s]
    [sayaka.proxy :as proxy]
    [sayaka.http-server :as http]
    [taoensso.timbre :as timbre
     :refer [log trace debug info warn error fatal report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy get-env]]
    [clojure.java.io :as io])
  (:import (org.apache.commons.lang3 SystemUtils)))

(def is-root (= (System/getenv "USER") "root"))
(def is-linux SystemUtils/IS_OS_LINUX)

(defn check-environment []
  (assert is-root "user is not root")
  (assert is-linux "environment is not linux")
  (doseq [cmd c/commands]
    (assert
      (not-empty (s/find-executable cmd))
      (str "executable '" cmd "' cannot be located"))))

(defn monika-client [])
(defn monika-server []
  (info "M.O.N.I.K.A starting")
  (check-environment)
  (proxy/start-server)
  (s/call "iptables" "iptables" "-w" "10" "-A" "OUTPUT" "-p" "tcp" "-m" "owner" "--uid-owner" c/monika-user "--dport" "80" "-j" "REJECT")
  (s/call "iptables" "iptables" "-w" "10" "-A" "OUTPUT" "-p" "tcp" "-m" "owner" "--uid-owner" c/monika-user "--dport" "443" "-j" "REJECT")
  (info "M.O.N.I.K.A started"))

(defn monika-orbit [])

(defn -main
  ([] monika-client)
  ([mode]
   (case mode
     "--server" (monika-server)
     "--client" (monika-client)
     "--orbit" (monika-orbit))))
