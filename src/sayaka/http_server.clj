(ns sayaka.http-server
  (:require [org.httpkit.server :refer [run-server]]
            [clj-time.core :as t]))

(defn between [args]
  (:start-time)
  (:end-time)
  (:script)
  (:script-args))

(defn status [args]
  (:start-time)
  (:end-time)
  (:script)
  (:script-args))

(defn add-wheel [args]
  (:start-time)
  (:end-time)
  (:script)
  (:script-args))

(defn remove-wheel [args]
  (:start-time)
  (:end-time)
  (:script)
  (:script-args))

(defn restart-proxy [args]
  (:start-time)
  (:end-time)
  (:script)
  (:script-args))

(defn run-script [args]
  (case (:script args)
    "between" (between args)
    "status" (status args)
    "add-wheel" (add-wheel args)
    "remove-wheel" (remove-wheel args)
    "restart-proxy" (restart-proxy args)))

(defn serve [req]
  (let [signals (read-string (:body req))]
    (for [s signals]
      (apply run-script s)))
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    (str (t/time-now))})

(defn start-server []
  (run-server serve {:port 8080})
  (println "Server started on port 8080"))
