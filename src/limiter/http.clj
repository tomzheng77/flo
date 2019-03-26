(ns limiter.http
  (:require [org.httpkit.server :as ks]
            [java-time-literals.core]
            [org.httpkit.client :as kc]
            [limiter.constants :as c]))

(defn start-http-server
  "starts a generic HTTP server at the given port using the provided handler
  the handler takes in the request EDN and returns a response map"
  [port handler]
  (let [lock (new Object)]
    (ks/run-server
      (fn [request]
        (let [body (c/decrypt (slurp (.bytes (:body request)) :encoding "UTF-8"))]
          (locking lock
            (try (let [edn (read-string body)]
                   {:status  200
                    :headers {"Content-Type" "text/plain"}
                    :body    (c/encrypt (pr-str (handler edn)))})
                 (catch Throwable e
                   {:status  400
                    :headers {"Content-Type" "text/plain"}
                    :body    (c/encrypt (pr-str {:error (.getMessage e)
                                                 :stack (map str (.getStackTrace e))}))})))))
      {:port port})))

(defn unwrap [connection]
  (let [response @connection]
    (if (:body response)
      (read-string (c/decrypt (:body response)))
      response)))

(defn send-server
  [edn]
  (let [path (str "http://127.0.0.1:" c/server-port)]
    (unwrap
      (kc/post path {:body (c/encrypt (pr-str edn))}))))

(defn send-orbit
  [edn]
  (let [path (str "http://" c/orbit-address ":" c/orbit-port)]
    (unwrap
      (kc/post path {:body (c/encrypt (pr-str edn))}))))
