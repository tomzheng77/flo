(ns octavia.restrictions
  (:require [clojure.java.io :as io]
            [octavia.constants :as c]
            [octavia.subprocess :as s]
            [clojure.string :as str])
  (:use [octavia.utils]))

(defn force-logout []
  (s/call "killall" "-u" c/user "i3")
  (s/call "killall" "-u" c/user "gnome-session-binary"))

(defn kill-within
  ([dir] (kill-within dir (s/process-report)))
  ([dir report]
   (doseq [process report]
     (if (str/starts-with? (:exe process) (canonical-path dir))
       (s/call "kill" (:pid process))))))

(defn list-files [path] (vec (.listFiles (io/file path))))

(defn close-all-browsers
  "stops any process whose executable is inside a directory
  linked by the browsers directory"
  []
  (let [report (s/process-report)]
    (doseq [browser-dir (list-files c/user-browsers)]
      (kill-within browser-dir report))))

(defn change-groups
  "changes the secondary groups of the user with a function"
  [update]
  (let [primary-group (:stdout (s/call "id" "-gn" c/user))
        all-groups (set (map str/trim (drop 2 (str/split (:stdout (s/call "groups" c/user)) #" "))))
        secondary-groups (disj all-groups primary-group)]
    (s/call "usermod" "-G" (str/join "," (update secondary-groups)) c/user)))

(defn add-wheel [] (change-groups (fn [groups] (conj groups "wheel"))))
(defn remove-wheel [] (change-groups (fn [groups] (disj groups "wheel"))))

(defn user-755 [file]
  (s/call "chown" (str c/user ":" c/user) (canonical-path file))
  (s/call "chmod" "755" (canonical-path file)))

(defn root-755 [file]
  (s/call "chown" "root:root" (canonical-path file))
  (s/call "chmod" "755" (canonical-path file)))

(defn root-700 [file]
  (s/call "chown" "root:root" (canonical-path file))
  (s/call "chmod" "700" (canonical-path file)))

(defn disable-login [] (s/call "passwd" "-l" c/user))
(defn enable-login [] (s/call "passwd" "-l" c/user))

(defn clear-all-restrictions []
  (enable-login)
  (doseq [dir (list-files c/user-projects)] (user-755 dir))
  (doseq [dir (list-files c/user-programs)] (root-755 dir)))

(defn recover
  "grants the user all access in order to recover
  from a catastrophic failure"
  []
  (add-wheel)
  (clear-all-restrictions))

(defn restrict-dirs
  "restricts and unlocks dirs using a predicate on directory name"
  [allow-name]
  (root-755 c/user-projects)
  (root-755 c/user-programs)
  (let [report (s/process-report)]
    (do
      (doseq [dir (list-files c/user-projects)]
        (if (allow-name (.getName dir))
          (user-755 dir)
          (do (root-700 dir) (kill-within dir report))))
      (doseq [dir (list-files c/user-programs)]
        (if (allow-name (.getName dir))
          (root-755 dir)
          (do (root-700 dir) (kill-within dir report)))))))
