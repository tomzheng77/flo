(ns sayaka.http-server
  (:require [org.httpkit.server :refer [run-server]]
            [clj-time.core :as t]))

(defn run-script [output script & args])

(defn serve [req]
  (let [signals (read-string (:body req))]
    (doseq [s signals]
      (apply run-script signals)
      {:status  200
       :headers {"Content-Type" "text/plain"}
       :body    (str (t/time-now))})))

(defn start-server []
  (run-server serve {:port 8080})
  (println "Server started on port 8080"))
