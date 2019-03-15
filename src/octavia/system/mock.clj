(ns octavia.system.mock
  (:require [octavia.constants :as c]
            [clojure.string :as str]
            [octavia.utils :as u]))

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
     :contents ""}))

(defn new-filesystem
  (new-folder "/" :root
    (new-folder "home" :root
      (new-folder c/user
        (new-folder "Documents"
          (new-folder "Projects")
          (new-folder "Programs")
          (new-folder "Browsers"))
        (new-folder "octavia"
          (new-file "octavia.json")
          (new-file "octavia.file"))))))

(def state (atom {:files         (new-filesystem)
                  :groups        #{c/user "wireshark"}
                  :can-login     true
                  :has-login     false
                  :screen-locked false}))

(defn test-url
  [url]
  )

;(defn mkdir
;  [path]
;  (loop [at new-dir list (reverse (u/split c/file-separator path))]
;    (if (empty? list)
;      at
;      (recur (assoc new-dir :files {(first list) at})
;             (rest list)))))
