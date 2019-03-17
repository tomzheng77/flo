(ns octavia.system.mock
  (:require [octavia.constants :as c]
            [clojure.string :as str]
            [octavia.utils :as u]
            [clojure.core.async :refer [go go-loop chan <! >! >!! <!!]]
            [clojure.core.match :refer [match]]))

(defn file?
  [item]
  (or (= (:type item) :folder)
      (= (:type item) :file)))

(defn parse-chown
  [args]
  (if (some #(= :root %) args)
    "root:root"
    (str c/user ":" c/user)))

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

(def state (atom {:filesystem    (new-filesystem)
                  :groups        #{c/user "wireshark"}
                  :can-login     true
                  :has-login     false
                  :screen-locked false}))

(defn mkdirs
  [state path]
  (:files))

(def system
  (let [messages (chan)]
    (go-loop []
      (match (<! messages)
        [:mkdirs path] (swap! state #(mkdirs % path))
        [:add-wheel] (println "add wheel")
        [:remove-wheel] (println "remove wheel")
        [:chmod] (println "chmod")
        [:chown] (println "chown"))
      (recur))
    messages))
