(ns octavia.system.mock
  (:require [octavia.constants :as c]
            [clojure.string :as str]
            [octavia.utils :as u]))

(def new-file {:type :file :content ""})
(def new-dir {:type :dir :files {}})
(def initial-root new-dir)
(def initial-groups #{c/user "wireshark"})

; represents the root of the filesystem
; each file can be {:type :dir :files {"name" ...}} or {:type :file :content ""}
(def root (atom initial-root))

; represents the group names of the user
(def groups (atom initial-groups))

; resets the system to it's initial state
(defn reset
  (reset! root initial-root)
  (reset! groups initial-groups))

(defn mkdir
  [path]
  (loop [at new-dir list (reverse (u/split c/file-separator path))]
    (if (empty? list)
      at
      (recur (assoc new-dir :files {(first list) at})
             (rest list)))))
