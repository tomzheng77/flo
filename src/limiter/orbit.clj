(ns limiter.orbit
  (:require [limiter.http :refer [start-http-server]]
            [limiter.constants :as c]
            [java-time-literals.core]
            [taoensso.timbre :as timbre :refer [trace debug info error]]
            [taoensso.timbre.appenders.core :as appenders])
  (:import (java.time LocalDateTime)
           (java.util Timer TimerTask)))

["this is a note"]
[{:name "A" :time (LocalDateTime/now)}]

(def notes (atom []))
(def verifications (atom []))

(defn add-note
  [note]
  (swap! notes #(conj % note)))

(defn add-verification
  [name time]
  (swap!
    verifications
    #(conj % {:name name :time time})))

(defn handle-request [edn]
  (case (:type edn)
    :add-note
    (let [note (:note edn)]
      (assert (string? note))
      (add-note note))

    :add-verification
    (locking verifications
      (let [name (:name edn)
            time (:time edn)]
        (assert (string? name))
        (assert (not-any? #(= name (:name %)) @verifications))
        (assert (instance? LocalDateTime time))
        (add-verification name time)))

    :verify
    (locking verifications
      (let [name (:name edn)]
        (swap! verifications (fn [vs] (filter #(not= name (:name %)) vs)))))

    :status
    (locking verifications
      (if (not-empty @verifications)
        {:verifications @verifications}
        {:notes @notes}))))

(defn check-verifications []
  (locking verifications
    (let [time (LocalDateTime/now)]
      (let [overdue (filter #(.isBefore (:time %) time) @verifications)]
        (when (not-empty overdue)
          (reset! notes [])
          (reset! verifications []))))))

(defn run []
  (start-http-server c/orbit-port handle-request)
  (let [timer (new Timer)]
    (.schedule timer (proxy [TimerTask] [] (run [] (check-verifications))) 0 1000)))
