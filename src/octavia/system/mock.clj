(ns octavia.system.mock
  (:require [octavia.constants :as c]
            [clojure.string :as str]
            [octavia.utils :as u]
            [clojure.core.async :refer [go go-loop chan <! >! >!! <!!]]
            [clojure.core.match :refer [match]]
            [clojure.string :as string])
  (:import (java.util.regex Pattern)))

(defn file?
  [item]
  (or (= (:type item) :folder)
      (= (:type item) :file)))

(defn parse-chown
  [args]
  (if (some #(= :root %) args)
    "root:root"
    (str c/user ":" c/user)))

(defn name= [name] (fn [file] (= name (:name file))))

(defn new-folder
  [name & args]
  (let [chown (parse-chown args)]
    {:name  name
     :type  :folder
     :chown chown
     :chmod "755"
     :files (filter file? args)}))

(defn new-file
  [name & args]
  (let [chown (parse-chown args)]
    {:name  name
     :type  :file
     :chown chown
     :chmod "755"
     :text  ""}))

(defn new-link
  [name target & args]
  (let [chown (parse-chown args)]
    {:name   name
     :type   :link
     :chown  chown
     :chmod  "755"
     :target target}))

(defn new-filesystem
  []
  (new-folder
    "/" :root
    (new-folder
      "home" :root
      (new-folder
        c/user
        (new-folder
          "Documents"
          (new-folder "Projects")
          (new-folder "Programs")
          (new-folder "Browsers"))
        (new-folder
          "octavia"
          (new-file "octavia.json")
          (new-file "octavia.file"))))))

(defn mkdirs
  [state path]
  (:files))

(defn to-path-seq
  [path]
  (if (string? path)
    (let [trim-path (str/trim path)]
      (if (empty? trim-path) [] (str/split trim-path (Pattern/quote c/file-separator))))
    path))

(defn chmod
  [at path perm]
  (let [path-seq (to-path-seq path)]
    (if (empty? path-seq)
      (assoc at :chmod perm)
      (let [next-step (some (name= (first path-seq)) (:files at))]
        (if (not (nil? next-step))
          (chmod next-step (next path-seq) perm))))))

(def system
  (let [messages (chan)
        state (atom {:filesystem    (new-filesystem)
                     :groups        #{c/user "wireshark"}
                     :can-login     true
                     :has-login     false
                     :screen-locked false})]
    (go-loop []
      (match (<! messages)
        [:read-string path return] (println "unknown")
        [:mkdirs path] (swap! state #(mkdirs % path))
        [:add-wheel] (swap! state #(assoc % :groups (conj (get % :groups) "wheel")))
        [:remove-wheel] (swap! state #(assoc % :groups (disj (get % :groups) "wheel")))
        [:chmod path perm] (swap! state #(chmod (:filesystem %) path perm))
        [:chown path owner] (println "chown"))
      (recur))
    messages))
