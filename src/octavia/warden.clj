(ns octavia.warden
  (:require [clojure.java.io :as io]
            [octavia.constants :as c]
            [octavia.subprocess :as s]
            [clojure.string :as str]))

(defn- ls [path] (vec (.listFiles (io/file path))))
(defn- canonical-path [path] (.getCanonicalPath (io/file path)))

(defn- kill-exe-in
  ([dir] (kill-exe-in dir (s/process-report)))
  ([dir report]
   (doseq [process report]
     (if (str/starts-with? (:exe process) (canonical-path dir))
       (s/call "kill" (:pid process))))))

(defn- change-groups
  "changes the secondary groups of the user with a function"
  [update]
  (let [primary-group (:out (s/call "id" "-gn" c/user))
        all-groups (set (map str/trim (drop 2 (str/split (:out (s/call "groups" c/user)) #" "))))
        secondary-groups (disj all-groups primary-group)]
    (s/call "usermod" "-G" (str/join "," (update secondary-groups)) c/user)))

(defn- ch [file user perm]
  (s/call "chown" (str user ":" user) (canonical-path file))
  (s/call "chmod" perm (canonical-path file)))

(defn- user-755 [file] (ch file c/user "755"))
(defn- root-755 [file] (ch file "root" "755"))
(defn- root-700 [file] (ch file "root" "700"))

(defn remove-wheel [] (change-groups #(disj % "wheel")))
(defn add-wheel [] (change-groups #(conj % "wheel")))

(defn disable-login [] (s/call "passwd" "-l" c/user))
(defn enable-login [] (s/call "passwd" "-l" c/user))

(defn lock-screen []
  (s/call "sudo" "-u" c/user "i3lock" "-n" "-c" "000000")
  (s/call "sudo" "-u" c/user "xdg-screensaver" "lock"))

(defn clear-all-restrictions []
  (enable-login)
  (user-755 c/user-projects)
  (user-755 c/user-programs)
  (doseq [dir (ls c/user-projects)] (user-755 dir))
  (doseq [dir (ls c/user-programs)] (root-755 dir)))

(defn resign
  "grants the user all access in order to recover
  from a catastrophic failure"
  []
  (add-wheel)
  (clear-all-restrictions)
  (System/exit 0))

(defn restrict-container
  "restricts and unlocks dirs inside a folder using a predicate on dir name"
  [container-dir report allow-name unlock-dir]
  (doseq [dir (ls container-dir)]
    (if (allow-name (.getName dir))
      (unlock-dir dir)
      (do (root-700 dir) (kill-exe-in dir report)))))

(defn block-project
  "restricts and unlocks dirs using a predicate on dir name"
  [allow-name]
  (root-755 c/user-projects)
  (root-755 c/user-programs)
  (let [report (s/process-report)]
    (restrict-container c/user-projects report allow-name user-755)
    (restrict-container c/user-programs report allow-name root-755)))

(defn send-notify
  "sends a notification to the user via the message bus.
  dunst needs to be installed first"
  ([title] (send-notify title ""))
  ([title message]
   (let [user-id (read-string (str/trim (:out (s/call "id" "-u" c/user))))]
     (s/call "sudo" "-u" c/user "DISPLAY=${display:1:-1}"
           (str "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/" user-id "/bus")
           "notify-send" title message))))
