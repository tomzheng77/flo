(ns octavia.core
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set]
            [octavia.proxy :as proxy]
            [octavia.limiter :as limiter :refer [limiter-at drop-before]]
            [taoensso.timbre :as timbre]
            [octavia.constants :as c])
  (:import (java.time LocalDateTime ZoneOffset)
           (java.util Timer TimerTask)))

(defn activate-limiter
  [limiter]
  (reset! proxy/block-host (:block-host limiter))
  (when (:block-login limiter)
    ; set no login
    ; activate i3lock
    )
  (if))

; the last limiter that was activated
(def prev-limiter (atom nil))

; this method should be called once per second
(defn on-enter-second []
  (let [now (LocalDateTime/now)
        limiters (try (limiter/parse (slurp c/primary-db)) (catch Throwable _))
        limiter (limiter-at limiters now)
        limiters-optimized (drop-before limiters now)]
    (locking prev-limiter
      (when (not (= @prev-limiter limiter))
        (reset! prev-limiter limiter)
        (activate-limiter limiter)))
    (when (not (= limiters-optimized limiters))
      (try (spit c/primary-db (limiter/stringify limiters-optimized))
           (catch Throwable _)))))

(defn -main [& args]
  (proxy/start-server)
  (let [timer (new Timer)]
    (.schedule timer
      (proxy [TimerTask] []
        (run [] (on-enter-second))) 0 1000)))
