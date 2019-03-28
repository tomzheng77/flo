(ns limiter.core
  (:gen-class)
  (:require [limiter.proxy :as proxy]
            [limiter.warden :refer [lock-screen disable-login block-folder resign clear-all-restrictions
                                    remove-wheel add-firewall-rules enable-login restart-lock-if-present lock-home
                                    remove-locks send-notify]]
            [limiter.limiter :as limiter :refer [limiter-at drop-before]]
            [taoensso.timbre :as timbre :refer [trace debug info error]]
            [taoensso.timbre.appenders.core :as appenders]
            [limiter.constants :as c]
            [limiter.http :refer [start-http-server]]
            [limiter.orbit :as orbit]
            [java-time-literals.core]
            [taoensso.encore :as enc]
            [clojure.java.io :as io])
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

(defn create-limiter-edn-if-not-found []
  (let [edn-file (io/file c/primary-edn)]
    (when-not (.exists edn-file)
      (let [parent-folder (.getParentFile edn-file)]
        (.mkdirs parent-folder)
        (spit edn-file (pr-str nil))))))

(defn read-limiters []
  (limiter/parse (slurp c/primary-edn)))

(defn write-limiters
  [limiters]
  (spit c/primary-edn (limiter/stringify limiters)))

(defn activate-limiter
  [limiter]
  (if (:is-last limiter)
    (do (clear-all-restrictions)
        (reset! proxy/block-host #{}))
    (do (remove-wheel)
        (lock-home)
        (reset! proxy/block-host (into #{} (:block-host limiter)))
        (when (not-empty (:block-host limiter)) (add-firewall-rules))
        (if (:block-login limiter)
          (do (disable-login)
              (lock-screen))
          (do (enable-login)
              (remove-locks)))
        (block-folder
          #(not (contains? (:block-folder limiter) %))))))

; the last limiter that was activated
(def prev-limiter (atom nil))
(def notified (atom #{}))

; this method should be called once per second
(defn on-enter-second []
  (create-limiter-edn-if-not-found)
  (let [now (LocalDateTime/now)
        limiters (read-limiters)
        limiter (limiter-at limiters now)
        in-1-minute (limiter-at limiters (.plusMinutes now 1))
        limiters-optimized (drop-before limiters now)]
    (locking notified
      (when-not (@notified in-1-minute)
        (send-notify "in 1 minute" (pr-str in-1-minute))
        (swap! notified #(conj % in-1-minute))))
    (locking prev-limiter
      (when (not (= @prev-limiter limiter))
        (reset! prev-limiter limiter)
        (activate-limiter limiter)))
    (when (not (= limiters-optimized limiters))
      (write-limiters limiters-optimized))))

(defn handle-request
  [edn]
  (when (= :restart-proxy (:type edn))
    (future (proxy/start-server)))
  (when (= :limiter (:type edn))
    (let [start (:start edn)
          end (:end edn)
          now (LocalDateTime/now)]
      (assert (limiter/date-time? start))
      (assert (limiter/date-time? end))
      (assert (.isBefore start end))
      (assert (limiter/valid-limits? edn))
      (with-local-vars [limiters (read-limiters)]
        (var-set limiters (-> @limiters
                              (limiter/add-limiter start end edn)
                              (limiter/drop-before now)))
        (write-limiters @limiters)))))

(defmacro try-or-resign
  [& body]
  `(try (do ~@body) (catch Throwable e# (resign e#))))

(defn run []
  (info "starting limiter")
  (try-or-resign
    (proxy/start-server)
    (start-http-server c/server-port handle-request))
  (let [timer (new Timer)]
    (.schedule
      timer
      (proxy [TimerTask] []
        (run [] (try-or-resign (on-enter-second))))
      0 1000)))

(defn -main [& args]
  (case (first args)
    "server" (run)
    "orbit" (orbit/run)))