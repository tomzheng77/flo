(ns octavia.http-server
  (:require [org.httpkit.server :refer [run-server]]
            [clj-time.core :as t]
            [octavia.state :as st]
            [octavia.proxy :as proxy]
            [octavia.restrictions :as r]))

(defn between [args]
  (:start-time args)
  (:end-time args)
  (:block args)
  (:proxy args))

(defn status
  [args]
  (pr-str (:state args)))

(defn add-wheel
  [args]
  (if (-> args :state st/is-idle)
    (do (r/add-wheel) "user has been added to the wheel group")))

(defn remove-wheel
  [args]
  (if (-> args :state st/is-idle)
    (do (r/remove-wheel) "user has been removed from the wheel group")))

(defn restart-proxy
  [args]
  (proxy/start-server (-> args :state st/proxy-settings))
  "the proxy has been restarted")

(defn run-script
  [args]
  (let [args-ws (assoc args :state (st/read-state))]
    (case (:script args)
      "between" (between args-ws)
      "status" (status args-ws)
      "add-wheel" (add-wheel args-ws)
      "remove-wheel" (remove-wheel args-ws)
      "restart-proxy" (restart-proxy args-ws))))

(defn serve [req]
  {:status  200
   :headers {"Content-Type" "text/plain"}
   :body    (pr-str
              (let [signals (read-string (:body req))]
                (for [s signals]
                  (apply run-script s))))})

(defn start-server []
  (run-server serve {:port 8080})
  (println "Server started on port 8080"))
