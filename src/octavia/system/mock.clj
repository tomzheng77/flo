(ns octavia.system.mock
  (:require [octavia.constants :as c]
            [clojure.string :as str]
            [octavia.utils :as u]))

(def new-file {:content ""})
(def new-dir {:files {}})
(def initial-root new-dir)
(def initial-groups #{c/user "wireshark"})

(defn add-file [dir file])

; represents the root of the filesystem
; each file can be {:files {"name" ...}} or {:content ""}
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

(defn sync
  "syncs one file structure to another"
  ([from to] (sync from to false))
  ([from to replace]))
