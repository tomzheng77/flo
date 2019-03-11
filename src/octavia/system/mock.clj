(ns octavia.system.mock
  (:require [octavia.constants :as c]
            [clojure.string :as str]
            [octavia.utils :as u]))

; represents the root of the filesystem
; each file can be {:type :dir :files {}} or {:type :file :content ""}
(def root (atom {:type :dir :files {}}))

; represents the group names of the user
(def groups (atom #{c/user "wireshark"}))

(def new-file {:type :file :content ""})
(def new-dir {:type :dir :files {}})

(defn mkdirs
  [path]
  (let [path-list (u/split c/file-separator path)]
    (swap! root
      (fn [root]
        (loop [at root, to-walk path-list])))))
