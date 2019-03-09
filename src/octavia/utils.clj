(ns octavia.utils
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.util.regex Pattern)))

(defn mkdirs [file-path] (.mkdirs (io/file file-path)))
(defn canonical-path [file-path] (.getCanonicalPath (io/file file-path)))

(defn re-quote [s] (Pattern/quote s))

(defn split-with-string [separator string]
  (str/split string (re-pattern (re-quote separator))))

(defn is-executable-file
  [file-path]
  (let [file (io/file file-path)]
    (and
      (.exists file)
      (.isFile file)
      (.canExecute file))))

(defn is-readable-dir
  [file-path]
  (let [file (io/file file-path)]
    (and
      (.exists file)
      (.isDirectory file)
      (.canRead file))))

(defn take-before
  [str char]
  (let [index (str/index-of str char)]
    (if (nil? index) str (subs str 0 index))))
