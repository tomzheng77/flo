(ns limiter.core
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set]
            [org.httpkit.server :as ks]
            [limiter.proxy :as proxy]
            [limiter.warden :refer [lock-screen disable-login
                                    block-folder resign clear-all-restrictions
                                    remove-wheel add-firewall-rules]]
            [limiter.limiter :as limiter :refer [limiter-at drop-before]]
            [taoensso.timbre :as timbre :refer [trace debug info error]]
            [taoensso.timbre.appenders.core :as appenders]
            [limiter.constants :as c]
            [java-time-literals.core]
            [taoensso.encore :as enc])
  (:import (java.time LocalDateTime)
           (java.util Timer TimerTask)))


(defn ns-filter [f] (-> f enc/compile-ns-filter enc/memoize_))
(defn log-by-ns-pattern
  [ns-patterns & [{:keys [?ns-str config level] :as opts}]]
  (let [ns (or (some->> ns-patterns
                        (keys)
                        (filter #(and (string? %) ((ns-filter %) ?ns-str)))
                        (not-empty)
                        (apply max-key count)) :all)
        allow-level (get ns-patterns ns (get config :level))]
    (if (timbre/level>= level allow-level) opts)))

(timbre/merge-config!
  {:level      :trace
   :appenders  {:spit (appenders/spit-appender {:fname c/primary-log})}
   :middleware [(partial log-by-ns-pattern {"io.netty.*"        :info
                                            "org.littleshoot.*" :info
                                            :all                :debug})]})

(defn read-limiters []
  (limiter/parse (slurp c/primary-edn)))

(defn write-limiters
  [limiters]
  (spit c/primary-edn (limiter/stringify limiters)))

(defn activate-limiter
  [limiter]
  (if (:is-last limiter)
    (clear-all-restrictions)
    (do (remove-wheel)
        (reset! proxy/block-host (into #{} (:block-host limiter)))
        (when (not-empty :block-host) (add-firewall-rules))
        (when (:block-login limiter)
          (lock-screen)
          (disable-login))
        (block-folder
          #(not (contains? (:block-folder limiter) %))))))

; the last limiter that was activated
(def prev-limiter (atom nil))

; this method should be called once per second
(defn on-enter-second []
  (debug "on-enter-second")
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

(defn handle-request-edn
  [edn]
  (when (= :limiter (:type edn))
    (let [start (:start edn)
          end (:end edn)]
      (assert (limiter/date-time? start))
      (assert (limiter/date-time? end))
      (assert (.isBefore start end))
      (assert (limiter/valid-limits? edn))
      (with-local-vars [limiters (read-limiters)]
        (var-set limiters (limiter/add-limiter limiters start end edn))
        (write-limiters limiters))
      {:status  200
       :headers {"Content-Type" "text/plain"}})))

(defn handle-request
  [request]
  (let [body (slurp (.bytes (:body request)) :encoding "UTF-8")]
    (try (let [edn (read-string body)] (handle-request-edn edn))
         (catch Throwable e
           {:status  400
            :headers {"Content-Type" "text/plain"}
            :body    (.getMessage e)}))))

(defn start-server []
  (ks/run-server handle-request {:port c/server-port}))

(defmacro try-or-resign
  [& body]
  `(try (do ~@body) (catch Throwable e# (resign e#))))

(defn -main [& args]
  (info "starting limiter")
  (try-or-resign
    (proxy/start-server)
    (start-server))
  (let [timer (new Timer)]
    (.schedule
      timer
      (proxy [TimerTask] []
        (run [] (try-or-resign (on-enter-second))))
      0 1000)))
