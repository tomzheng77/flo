(ns sayaka.http-server
  (:require [org.httpkit.server :refer [run-server]]
            [clj-time.core :as t]))

(defn app [req]
  (println req)
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (str (t/time-now))})

(defn start-server []
  (run-server app {:port 8080})
  (println "Server started on port 8080"))
