(ns octavia.limiter
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set]
            [java-time-literals.core])
  (:import (java.time LocalDateTime ZoneOffset)))

; a single limiter, a limiter updates the profile of the user
; time - the time for this limiter to occur
; block-login - whether the user should be barred from login entirely (defaults to false)
; block-website - blocks any hosts which matches one of the strings (defaults to none)
; block-folder - blocks any projects which matches the name precisely (defaults to none)
{:time         (LocalDateTime/now)
 :block-login  false
 :block-host   #{"www.google.com" "anime" "manga"}
 :block-folder #{"clojure365"}}

; object limiters
; the next limiters, the last item will always be treated as an unlock
[{:time         (LocalDateTime/now)
  :block-login  false
  :block-host   #{"www.google.com" "anime" "manga"}
  :block-folder #{"clojure365"}}
 {:time         (LocalDateTime/now)
  :block-login  false
  :block-host   #{"www.google.com" "anime" "manga"}
  :block-folder #{"clojure365"}}
 {:time (LocalDateTime/now)}]

(defn stringify [limiters] (pr-str limiters))
(defn parse [edn] (read-string edn))

(defn union [val-1 val-2]
  (cond
    (and (set? val-1) (set? val-2)) (set/union val-1 val-2)
    true (or val-1 val-2)))

(defn extend-limiter
  "extends the limiter with one or more limits"
  [limiter & limits]
  (loop [updated-limiter limiter remaining limits]
    (if (empty? remaining)
      updated-limiter
      (recur (merge-with union updated-limiter (first remaining))
             (next remaining)))))

(defn sort-by-time
  [limiters]
  (sort-by #(.toEpochSecond (:time %) (ZoneOffset/UTC)) limiters))

(defn nil-or [f x] (or (nil? x) (f x)))
(defn date-time? [x] (instance? LocalDateTime x))

(defn valid?
  [limiter]
  (and
    (instance? LocalDateTime (:time limiter))
    (nil-or boolean? (:block-login limiter))
    (nil-or set? (:block-host limiter))
    (nil-or set? (:block-folder limiter))))

(defn valid-limits?
  [limits]
  (and
    (nil-or boolean? (:block-login limits))
    (nil-or set? (:block-host limits))
    (nil-or set? (:block-folder limits))))

(defn between?
  "checks if time is between start and end"
  [time start end]
  (and
    (not (.isBefore time start))
    (not (.isAfter time end))))

(defn equiv?
  [a b]
  (and (= (true? (:block-login a)) (true? (:block-login b)))
       (= (into #{} (:block-host a)) (into #{} (:block-host b)))
       (= (into #{} (:block-folder a)) (into #{} (:block-folder b)))))

(defn remove-last-limits
  [limiters]
  (if (empty? limiters)
    limiters
    (concat (butlast limiters) [{:time (:time (last limiters))}])))

(defn remove-duplicate
  "removes any limiter which has the same limits as the previous"
  [limiters]
  (loop [in limiters out []]
    (if (empty? in)
      out
      (if (and (not= 1 (count in))
               (not-empty out)
               (equiv? (first in) (last out)))
        (recur (next in) out)
        (recur (next in) (conj out (first in)))))))

(defn merge-same-time
  "extends any two limiters that collide into one limiter"
  [limiters]
  (map #(apply extend-limiter (val %)) (group-by :time limiters)))

(defn add-limiter
  "applies the limits to a list of limiters such that
  the effects of the limit will be respected between {start} and {end}"
  [limiters start end limits]
  (let [before (filter #(.isBefore (:time %) start) limiters)
        between (filter #(between? (:time %) start end) limiters)
        after (filter #(.isAfter (:time %) end) limiters)
        before-end (last (filter #(.isBefore (:time %) end) limiters))]
    (remove-last-limits
      (remove-duplicate
        (merge-same-time
          (filter
            valid?
            (concat
              before [(assoc limits :time start)]
              (map #(extend-limiter % limits) between)
              [(assoc before-end :time end)] after)))))))

(defn filter-not
  ([pred] (filter #(not (pred %))))
  ([pred coll] (filter #(not (pred %)) coll)))

(defn drop-before
  "removes all limiters before time EXCEPT the last one"
  [limiters time]
  (let [removed (filter #(.isBefore (:time %) time) limiters)
        remaining (filter-not #(.isBefore (:time %) time) limiters)]
    (concat [(last (sort-by-time removed))] remaining)))

(defn limiter-at
  "finds the limiter which should be effective at {time}"
  [limiters time]
  (let [remain (sort-by-time (drop-before limiters time))]
    (assoc (first remain)
      :is-last (<= (count remain) 1))))
