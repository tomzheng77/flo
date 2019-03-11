(ns octavia.mock-system
  (:require [octavia.constants :as c]
            [clojure.string :as str]
            [octavia.utils :as u]))

; represents the root of the filesystem
; each file can be {:type :folder :files []} or {:type :file :content ""}
(def root (atom {}))

; represents the group names of the user
(def groups (atom #{c/user "wireshark"}))

(defn mkdirs
  [path]
  (u/split c/file-separator path))