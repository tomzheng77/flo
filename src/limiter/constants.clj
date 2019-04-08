(ns limiter.constants
  (:require [clojure.string :as str]
            [clojure.set :refer [union]]
            [taoensso.nippy :as nippy])
  (:import (java.io File)
           (java.util.regex Pattern)
           (java.nio.charset Charset)))

(def encoding "UTF-8")
(def charset (Charset/forName encoding))

(def file-separator (File/separator))
(def path-separator (File/pathSeparator))

(def custom-paths
  #{"/usr/bin"
    "/usr/sbin"
    "/usr/local/bin"})

(def raw-paths (set (str/split (System/getenv "PATH") (re-pattern (Pattern/quote path-separator)))))
(def global-paths (union custom-paths raw-paths))
(def global-path (str/join path-separator global-paths))

(def proxy-port 9003)
(def server-port 9001)
(def orbit-address "103.29.84.69")
(def orbit-port 9002)

(def user "tomzheng")
(def user-projects (str "/home/" user "/Documents/Projects"))
(def user-programs (str "/home/" user "/Documents/Programs"))
(def processes-dir "/proc")

(def home (str "/home/" user "/limiter"))
(def primary-log (str home "/limiter.log"))
(def primary-edn (str home "/limiter.edn"))

(def password "z&YwCvso;>MTt0ll&lfL)h^mps{]*Q{+")
(defn encrypt [edn] (nippy/freeze edn {:password [:salted password]}))
(defn decrypt [bytes] (nippy/thaw bytes {:password [:salted password]}))
