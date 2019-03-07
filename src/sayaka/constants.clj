(ns sayaka.constants
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

(def user "tomzheng")
(def user-projects (str "/home/" user "/Documents/Projects"))
(def user-programs (str "/home/" user "/Documents/Programs"))
(def user-browsers (str "/home/" user "/Documents/Browsers"))
(def processes-dir "/proc")

(def home (str "/home/" user "/sayaka"))
(def primary-log (str home "/sayaka.log"))
(def primary-db (str home "/sayaka.edn"))

(def certificate-root (str home "/certs"))
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
