(ns sayaka.utils
  (:require [clojure.java.io :as io]))

(defn mkdirs [file-path] (.mkdirs (io/file file-path)))
(defn canonical-path [file-path] (.getCanonicalPath (io/file file-path)))
