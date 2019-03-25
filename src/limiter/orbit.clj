(ns limiter.orbit
  (:require [limiter.http :refer [start-http-server]]
            [limiter.constants :as c])
  (:import (java.time LocalDateTime)))

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
  (when (= :add-note (:type edn))
    (let [note (:note edn)]
      (add-note note)
      {:status  200
       :headers {"Content-Type" "text/plain"}}))
  (when (= :add-verification (:type edn))
    (locking verifications
      (let [name (:name edn)
            time (:time edn)]
        (assert (not-any? #(= name (:name %)) @verifications))
        (assert (instance? LocalDateTime time))
        (add-verification name time)
        {:status  200
         :headers {"Content-Type" "text/plain"}})))
  (when (= :status (:type edn))
    (locking verifications
      (if (not-empty @verifications)
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body (pr-str {:verifications @verifications})}))))

(defn run []
  (start-http-server c/orbit-port handle-request))
