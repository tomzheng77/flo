(ns octavia.core
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set]
            [org.httpkit.server :as ks]
            [octavia.proxy :as proxy]
            [octavia.warden :refer [lock-screen disable-login block-folder resign]]
            [octavia.limiter :as limiter :refer [limiter-at drop-before]]
            [taoensso.timbre :as timbre]
            [octavia.constants :as c])
  (:import (java.time LocalDateTime)
           (java.util Timer TimerTask)))

(defn activate-limiter
  [limiter]
  (reset! proxy/block-host (:block-host limiter))
  (when (:block-login limiter)
    (lock-screen)
    (disable-login))
  (block-folder
    #(not-any? #{%} (:block-folder limiter))))

; the last limiter that was activated
(def prev-limiter (atom nil))

; this method should be called once per second
(defn on-enter-second []
  (println "run each second")
  (let [now (LocalDateTime/now)
        limiters (try (limiter/parse (slurp c/primary-db)) (catch Throwable _ (resign)))
        limiter (limiter-at limiters now)
        limiters-optimized (drop-before limiters now)]
    (locking prev-limiter
      (when (not (= @prev-limiter limiter))
        (reset! prev-limiter limiter)
        (activate-limiter limiter)))
    (when (not (= limiters-optimized limiters))
      (try (spit c/primary-db (limiter/stringify limiters-optimized))
           (catch Throwable _ (resign))))))

(defn start-server []
  (ks/run-server
    (fn [request]
      (let [body (str (:body request))]
        (try (let [edn (read-string body)
                   start (:start edn)
                   end (:end edn)
                   block-login (:block-login edn)
                   block-host (:block-host edn)
                   block-folder (:block-folder edn)])
             (catch Throwable e
               {:status  400
                :headers {"Content-Type" "text/plain"}
                :body    (.getMessage e)}))))
    {:port c/server-port}))

(defn -main [& args]
  (println "starting octavia")
  (proxy/start-server)
  (start-server)
  (let [timer (new Timer)]
    (.schedule
      timer
      (proxy [TimerTask] []
        (run [] (on-enter-second))) 0 1000)))
