(ns octavia.core
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set])
  (:import (java.time LocalDateTime ZoneOffset)))

; a single limiter, a limiter updates the profile of the user
; time - the time for this limiter to occur
; block-login - whether the user should be barred from login entirely (defaults to false)
; block-website - blocks any hosts which matches one of the strings (defaults to none)
; block-project - blocks any projects which matches the name precisely (defaults to none)
{:time          (LocalDateTime/now)
 :block-login   false
 :block-host    #{"www.google.com" "anime" "manga"}
 :block-project #{"clojure365"}}

; object limiters
; the next limiters, the last item will always be treated as an unlock
[{:time          (LocalDateTime/now)
  :block-login   false
  :block-host    #{"www.google.com" "anime" "manga"}
  :block-project #{"clojure365"}}
 {:time          (LocalDateTime/now)
  :block-login   false
  :block-host    #{"www.google.com" "anime" "manga"}
  :block-project #{"clojure365"}}
 {:time (LocalDateTime/now)}]

(defn add-limits-to-limiter
  "increase the limits of the limiter by the given limits"
  [limiter limits]
  {:time          (:time limiter)
   :block-login   (or (:block-login limiter) (:block-login limits))
   :block-host    (set/union (:block-host limiter) (:block-host limits))
   :block-project (set/union (:block-project limiter) (:block-project limits))})

(defn sort-limiters
  [limiters]
  (sort-by #(.toEpochSecond (:time %) (ZoneOffset/UTC)) limiters))

(defn remove-nil [list]
  (filter #(not (nil? %)) list))

(defn between?
  "checks if time is between start and end"
  [time start end]
  (and
    (not (.isBefore time start))
    (not (.isAfter time end))))

(defn apply-limits
  "applies the limits to a list of limiters such that
  the effects of the limit will be respected between {start} and {end}"
  [limiters start end limits]
  (let [before (filter #(.isBefore (:time %) start) limiters)
        between (filter #(between? (:time %) start end) limiters)
        after (filter #(.isAfter (:time %) end) limiters)
        before-end (last (filter #(.isBefore (:time %) end) limiters))]
    (concat
      before [(assoc limits :time start)]
      (map #(add-limits-to-limiter % limits) between)
      [(assoc before-end :time end)] after)))

(defn filter-not
  ([pred] (filter #(not (pred %))))
  ([pred coll] (filter #(not (pred %)) coll)))

(defn remove-before
  "removes all limiters before time EXCEPT the last one"
  [limiters time]
  (let [removed (filter #(.isBefore (:time %) time) (:next limiters))
        remaining (filter-not #(.isBefore (:time %) time) (:next limiters))]
    (concat [(last removed)] remaining)))

(defn limiter-at
  "finds the limiter which should be effective at {time}"
  [limiters time]
  (first (remove-before limiters time)))

(defn mins [x] (.plusMinutes (LocalDateTime/now) x))
(defn arbitrary [mins])
