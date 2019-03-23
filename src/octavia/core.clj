(ns octavia.core
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set]
            [octavia.proxy :as proxy]
            [octavia.warden :refer [lock-screen disable-login block-project resign]]
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
  (block-project
    #(not-any? #{%} (:block-project limiter))))

; the last limiter that was activated
(def prev-limiter (atom nil))

; this method should be called once per second
(defn on-enter-second []
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

(defn start-server
  (org.httpkit.server/run-server #(println %) {:port c/server-port}))

(defn -main [& args]
  (proxy/start-server)
  (start-server)
  (let [timer (new Timer)]
    (.schedule
      timer
      (proxy [TimerTask] []
        (run [] (on-enter-second))) 0 1000)))
