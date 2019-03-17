(ns octavia.system.mock
  (:require [octavia.constants :as c]
            [clojure.string :as str]
            [octavia.utils :as u]
            [clojure.core.async :refer [go go-loop chan <! >! >!! <!!]]
            [clojure.core.match :refer [match]]
            [clojure.string :as string])
  (:import (java.util.regex Pattern)))

(defn- file?
  [item]
  (or (= (:type item) :folder)
      (= (:type item) :file)))

(defn- parse-chown
  [args]
  (if (some #(= :root-owned %) args)
    "root:root"
    (str c/user ":" c/user)))

(defn- name= [name] (fn [file] (= name (:name file))))

(defn- new-folder
  [name & args]
  (let [chown (parse-chown args)]
    {:name  name
     :type  :folder
     :chown chown
     :chmod "755"
     :files (filter file? args)}))

(defn- new-file
  [name & args]
  (let [chown (parse-chown args)]
    {:name  name
     :type  :file
     :chown chown
     :chmod "755"
     :text  ""}))

(defn- new-link
  [name target & args]
  (let [chown (parse-chown args)]
    {:name   name
     :type   :link
     :chown  chown
     :chmod  "755"
     :target target}))

(defn- new-filesystem
  []
  (new-folder
    "/" :root-owned
    (new-folder
      "home" :root-owned
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

(defn- to-path-seq
  [path]
  (if (string? path)
    (let [trim-path (str/trim path)]
      (->> c/file-separator
           (Pattern/quote)
           (str/split trim-path)
           (filter not-empty)))
    path))

(defn- valid-perm?
  [perm]
  (and (string? perm) (re-matches #"[0-7]{3}" perm)))

(defn- change-chmod
  "changes the :chmod property of a file at the specified path"
  [at-file path perm]
  (let [path-seq (to-path-seq path)]
    (if (empty? path-seq)
      (assoc at-file :chmod perm)
      (let [next-step (some (name= (first path-seq)) (:files at-file))]
        (if (not (nil? next-step))
          (change-chmod next-step (next path-seq) perm))))))

(defn- change-chown
  "changes the :chown property of a file at the specified path"
  [at-file path owner]
  (let [path-seq (to-path-seq path)]
    (if (empty? path-seq)
      (assoc at-file :chmod owner)
      (let [next-step (some (name= (first path-seq)) (:files at-file))]
        (if (not (nil? next-step))
          (change-chown next-step (next path-seq) owner))))))

(defmacro call
  [channel & args])

(def system
  (let [messages (chan 1000)
        state (atom {:filesystem    (new-filesystem)
                     :groups        #{c/user "wireshark"}
                     :can-login     true
                     :has-login     false
                     :screen-locked false})]
    (go-loop []
      (let [message (<! messages)]
        (match message
          [:state return] (>!! return state)
          [:read-string path return] (println "read-string")
          [:mkdirs path] (println "mkdirs")
          [:add-group group] (swap! state #(assoc % :groups (conj (get % :groups) group)))
          [:remove-group group] (swap! state #(assoc % :groups (disj (get % :groups) group)))
          [:chmod path perm] (if (valid-perm? perm) (swap! state #(assoc % :filesystem (chmod (:filesystem %) path perm))))
          [:chown path owner] (swap! state #(assoc % :filesystem (chmod (:filesystem %) path owner)))))
      (recur))
    messages))
