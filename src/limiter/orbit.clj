(ns limiter.orbit
  (:require [limiter.http :refer [start-http-server]]
            [limiter.constants :as c]
            [java-time-literals.core]
            [taoensso.timbre :as timbre :refer [trace debug info error]]
            [taoensso.timbre.appenders.core :as appenders]
            [clojure.core.match :refer [match]])
  (:import (java.time LocalDateTime)
           (java.util Timer TimerTask)))

["this is a note"]
[{:name "A" :time (LocalDateTime/now) :window 10}]

(def notes (atom []))
(def verifications (atom []))

(defn add-note
  [note]
  (swap! notes #(conj % note)))

(defn add-verification
  [name time window]
  (swap!
    verifications
    #(conj % {:name name :time time :window window})))

; removes any verification which the predicate f(v) = true
(defn remove-if [f]
  (swap! verifications
    (fn [vs]
      (filter #(not (f %)) vs))))

(defn handle-request [edn]
  (info "received request, type =" (:type edn))
  (match (:type edn)
    :add-note
    (let [note (:note edn)]
      (assert (string? note))
      (let [new-notes (add-note note)]
        (if (not-empty @verifications)
          nil
          new-notes)))

    :add-verification
    (locking verifications
      (let [{:keys [name time window]} edn]
        (assert (string? name))
        (assert (not-any? #(= name (:name %)) @verifications))
        (assert (instance? LocalDateTime time))
        (assert (or (nil? window) (and (integer? window) (< 0 window))))
        (add-verification name time (or window 10))))

    :verify
    (locking verifications
      (let [v-name (:name edn) now (LocalDateTime/now)]
        (remove-if
          (fn [{:keys [name time window]}]
            (and (= v-name name)
                 (.isAfter now (.minusMinutes time window)))))))

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
          (info "overdue" (vec overdue))
          (reset! notes [])
          (reset! verifications []))))))

(def stop-fn (atom nil))
(defn run []
  (locking stop-fn
    (if @stop-fn (@stop-fn))
    (->> (start-http-server c/orbit-port handle-request)
         (reset! stop-fn)))
  (info "started HTTP server")
  (let [timer (new Timer)]
    (.schedule timer (proxy [TimerTask] [] (run [] (check-verifications))) 0 1000)))
