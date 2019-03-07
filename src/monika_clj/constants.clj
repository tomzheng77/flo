(ns monika-clj.constants
  (:require [clojure.string :as str])
  (:require [clojure.set :as set :refer [union]])
  (:import (java.io File)
           (java.util.regex Pattern)
           (java.nio.charset Charset)))

(defn re-quote [s] (Pattern/quote s))

(defn split-with-string [separator string]
  (str/split string (re-pattern (re-quote separator))))

(def encoding "UTF-8")
(def charset (Charset/forName encoding))

(def file-separator (File/separatorChar))
(def path-separator (File/pathSeparator))

(def custom-paths
  #{"/usr/bin"
    "/usr/sbin"
    "/usr/local/bin"})

(def raw-paths (set (split-with-string path-separator (System/getenv "PATH"))))
(def global-paths (union custom-paths raw-paths))

(def proxy-port 9000)
(def interpreter-port 9001)
(def orbit-address "103.29.84.69")
(def orbit-port 9002)

(def monika-user "tomzheng")
(def monika-user-projects (str "/home/" monika-user "/Documents/Projects"))
(def monika-user-programs (str "/home/" monika-user "/Documents/Programs"))
(def monika-user-browsers (str "/home/" monika-user "/Documents/Browsers"))
(def processes-dir "/proc")

(def monika-home (System/getenv "MONIKA_HOME"))
(assert (not= monika-home nil) "MONIKA_HOME not found, please check /etc/environment")

(def primary-log (str monika-home "/monika.log"))
(def primary-db (str monika-home "/monika.edn"))

(def certificate-root (str monika-home "/certs"))
(def certificate (str certificate-root "/certificate.cer"))
(def private-key (str certificate-root "/private-key.pem"))
(def key-store (str certificate-root "/private-key.pem"))

(def commands
  #{"id"
    "groups"
    "passwd"
    "chmod"
    "chown"
    "iptables"
    "usermod"
    "groupadd"
    "killall"
    "kill"})
