(ns octavia.restrictions
  (:require [clojure.java.io :as io]
            [octavia.constants :as c]
            [octavia.subprocess :as s]
            [octavia.utils :as u]
            [clojure.string :as str]))

(defn force-logout []
  (s/call "killall" "-u" c/user "i3")
  (s/call "killall" "-u" c/user "gnome-session-binary"))

(defn kill-within
  ([dir] (kill-within dir (s/process-report)))
  ([dir report]
   (doseq [process report]
     (if (str/starts-with? (:exe process) (u/canonical-path dir))
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
  (let [primary-group (:out (s/call "id" "-gn" c/user))
        all-groups (set (map str/trim (drop 2 (str/split (:out (s/call "groups" c/user)) #" "))))
        secondary-groups (disj all-groups primary-group)]
    (s/call "usermod" "-G" (str/join "," (update secondary-groups)) c/user)))

(defn add-wheel [] (change-groups (fn [groups] (conj groups "wheel"))))
(defn remove-wheel [] (change-groups (fn [groups] (disj groups "wheel"))))

(defn ch [file user perm]
  (s/call "chown" (str user ":" user) (u/canonical-path file))
  (s/call "chmod" perm (u/canonical-path file)))

(defn user-755 [file] (ch file c/user "755"))
(defn root-755 [file] (ch file "root" "755"))
(defn root-700 [file] (ch file "root" "700"))

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
