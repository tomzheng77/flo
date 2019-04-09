(ns limiter.core
  (:gen-class)
  (:require [limiter.proxy :as proxy]
            [limiter.warden :refer [lock-screen disable-login block-folder resign clear-all-restrictions
                                    remove-wheel add-firewall-rules enable-login lock-home unlock-screen send-notify
                                    user-755]]
            [limiter.limiter :as limiter :refer [limiter-at drop-before]]
            [taoensso.timbre :as timbre :refer [trace debug info error]]
            [taoensso.timbre.appenders.core :as appenders]
            [limiter.constants :as c]
            [limiter.http :refer [start-http-server]]
            [limiter.orbit :as orbit]
            [java-time-literals.core]
            [taoensso.encore :as enc]
            [clojure.java.io :as io]
            [clojure.core.async :refer [go-loop <! chan >!!]]
            [clojure.core.match :refer [match]])
  (:import (java.time LocalDateTime)
           (java.util Timer TimerTask)
           (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

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
              (unlock-screen)
              (lock-screen))
          (do (enable-login)
              (unlock-screen)))
        (block-folder
          #(not (contains? (:block-folder limiter) %))))))

; the last limiter that was activated
(def prev-limiter (atom nil))
(def notified (atom #{}))

; use an atom to store the current limiters
; this must be initialized first, but can then be
; used or modified concurrently
(def limiters (atom []))

; this will start a thread which continuously writes
; the latest version of limiters
(let [limiters-last-write (atom [])
      signals (chan)
      signal-count (atom 0)]
  ; whenever the value of limiters changes, add a new signal
  ; the signal can be any value
  (add-watch limiters :rewrite
    (fn [_ _ _ _]
      (when (> 512 @signal-count)
        (swap! signal-count inc)
        (>!! signals 0))))
  (go-loop []
    (let [_ (<! signals)]
      (println "signal received")
      (swap! signal-count dec)
      (let [now-limiters @limiters]
        (when (not= now-limiters @limiters-last-write)
          (println "writing limiters")
          (write-limiters now-limiters)
          (reset! limiters-last-write now-limiters))))
    (recur)))

; this method should be called once per second
(defn on-enter-second []
  (let [now (LocalDateTime/now)
        now-limiters @limiters
        limiter (limiter-at now-limiters now)
        in-1-minute (limiter-at now-limiters (.plusMinutes now 1))]
    (locking notified
      (when-not (@notified in-1-minute)
        (send-notify "in 1 minute" (pr-str in-1-minute))
        (swap! notified #(conj % in-1-minute))))
    (locking prev-limiter
      (when (not (= @prev-limiter limiter))
        (reset! prev-limiter limiter)
        (activate-limiter limiter)))
    (swap! limiters #(drop-before % now))))

(defn handle-request
  [edn]
  (match (:type edn)
    :new-project
    (let [name (:name edn)]
      (assert (string? name))
      (let [dir (io/file c/user-projects name)]
        (assert (not (.exists dir)))
        (.mkdirs dir)
        (user-755 dir))))

    :new-program
    (let [path (:path edn)
          name (:name edn)]
      (assert (string? path))
      (assert (string? name))
      (let [link (io/file c/user-programs name)
            program (io/file path)]
        (assert (not (.exists link)))
        (assert (.exists program))
        (Files/createSymbolicLink
          (.toPath link)
          (.toPath program)
          (make-array FileAttribute 0)))

    :restart-proxy
    (future (proxy/start-server))

    :limiter
    (let [start (:start edn)
          end (:end edn)
          now (LocalDateTime/now)]
      (assert (limiter/date-time? start))
      (assert (limiter/date-time? end))
      (assert (.isBefore start end))
      (assert (limiter/valid-limits? edn))
      (swap! limiters
             #(-> % (limiter/add-limiter start end edn)
                    (limiter/drop-before now))))))


(defmacro try-or-resign
  [& body]
  `(try (do ~@body) (catch Throwable e# (resign e#))))

(defn run []
  (info "starting limiter")
  (create-limiter-edn-if-not-found)
  (reset! limiters (read-limiters))
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
