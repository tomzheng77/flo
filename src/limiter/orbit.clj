(ns limiter.orbit
  (:import (java.time LocalDateTime)))

{:notes [] :verifications [{:name "A" :time (LocalDateTime/now)}]}

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

(defn run-server
  [])
