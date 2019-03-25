(ns limiter.http
  (:require [org.httpkit.server :as ks]
            [java-time-literals.core]))

(defn start-http-server
  "starts a generic HTTP server at the given port using the provided handler
  the handler takes in the request EDN and returns a response map"
  [port handler]
  (ks/run-server
    (fn [request]
      (let [body (slurp (.bytes (:body request)) :encoding "UTF-8")]
        (try (let [edn (read-string body)]
               {:status  200
                :headers {"Content-Type" "text/plain"}
                :body    (pr-str (handler edn))})
             (catch Throwable e
               {:status  400
                :headers {"Content-Type" "text/plain"}
                :body    (.getMessage e)}))))
    {:port port}))
