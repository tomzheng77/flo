(ns octavia.constants
  (:use [octavia.utils])
  (:require [clojure.string :as str])
  (:require [clojure.set :as set :refer [union]])
  (:import (java.io File)
           (java.util.regex Pattern)
           (java.nio.charset Charset)))

(def encoding "UTF-8")
(def charset (Charset/forName encoding))

(def file-separator (File/separatorChar))
(def path-separator (File/pathSeparator))

(def custom-paths
  #{"/usr/bin"
    "/usr/sbin"
    "/usr/local/bin"})

(def raw-paths (set (split path-separator (System/getenv "PATH"))))
(def global-paths (union custom-paths raw-paths))
(def global-path (str/join path-separator global-paths))

(def proxy-port 9003)
(def interpreter-port 9004)
(def orbit-address "103.29.84.69")
(def orbit-port 9005)

(def user "tomzheng")
(def user-projects (str "/home/" user "/Documents/Projects"))
(def user-programs (str "/home/" user "/Documents/Programs"))
(def user-browsers (str "/home/" user "/Documents/Browsers"))
(def processes-dir "/proc")

(def home (str "/home/" user "/octavia"))
(def primary-log (str home "/octavia.log"))
(def primary-db (str home "/octavia.edn"))

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
