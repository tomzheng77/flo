(ns octavia.core
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set]
            [org.httpkit.server :as ks]
            [octavia.proxy :as proxy]
            [octavia.warden :refer [lock-screen disable-login block-folder resign]]
            [octavia.limiter :as limiter :refer [limiter-at drop-before boolean?]]
            [taoensso.timbre :as timbre]
            [octavia.constants :as c]
            [java-time-literals.core])
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

(defn read-limiters []
  (try (limiter/parse (slurp c/primary-db)) (catch Throwable _ (resign))))

(defn write-limiters
  [limiters]
  (try (spit c/primary-db (limiter/stringify limiters))
       (catch Throwable _ (resign))))

; this method should be called once per second
(defn on-enter-second []
  (println "run each second")
  (let [now (LocalDateTime/now)
        limiters (read-limiters)
        limiter (limiter-at limiters now)
        limiters-optimized (drop-before limiters now)]
    (locking prev-limiter
      (when (not (= @prev-limiter limiter))
        (reset! prev-limiter limiter)
        (activate-limiter limiter)))
    (when (not (= limiters-optimized limiters))
      (write-limiters limiters-optimized))))

(defn handle-request
  [request]
  (let [body (slurp (.bytes (:body request)) :encoding "UTF-8")]
    (try (let [edn (read-string body)
               start (:start edn)
               end (:end edn)]
           (println body)
           (println edn)
           (assert (limiter/date-time? start))
           (assert (limiter/date-time? end))
           (assert (limiter/valid-limits? edn))
           (with-local-vars [limiters (read-limiters)]
             (var-set limiters (limiter/apply-limits limiters start end edn))
             (write-limiters limiters))
           {:status  200
            :headers {"Content-Type" "text/plain"}})
         (catch Throwable e
           {:status  400
            :headers {"Content-Type" "text/plain"}
            :body    (.getMessage e)}))))

(defn start-server []
  (ks/run-server handle-request {:port c/server-port}))

(defn -main [& args]
  (println "starting octavia")
  (proxy/start-server)
  (start-server)
  (let [timer (new Timer)]
    (.schedule
      timer
      (proxy [TimerTask] []
        (run [] (on-enter-second))) 0 1000)))
