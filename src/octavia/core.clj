(ns octavia.core
  (:require [clojure.core.match :refer [match]]
            [clojure.set :as set])
  (:import (java.time LocalDateTime)))

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
; asap - a limiter which should be applied as soon as possible
; prev - the last limiter which was applied
; next - the next limiters, the last item will always be treated as an unlock
{:asap nil
 :prev nil
 :next [{:time          (LocalDateTime/now)
         :block-login   false
         :block-host    #{"www.google.com" "anime" "manga"}
         :block-project #{"clojure365"}}
        {:time          (LocalDateTime/now)
         :block-login   false
         :block-host    #{"www.google.com" "anime" "manga"}
         :block-project #{"clojure365"}}
        {:time (LocalDateTime/now)}]}

(defn add-limits-to-limiter
  "increase the limits of the limiter by the given limits"
  [limiter limits]
  {:time          (:time limiter)
   :block-login   (or (:block-login limiter) (:block-login limits))
   :block-host    (set/union (:block-host limiter) (:block-host limits))
   :block-project (set/union (:block-project limiter) (:block-project limits))})

(defn remove-nil [list]
  (filter #(not (nil? %)) list))

(defn between?
  "checks if time is between start and end"
  [time start end]
  (and
    (not (.isBefore time start))
    (not (.isAfter time end))))

(defn apply-limits-to-list
  "applies the limits to a list of limiters such that
  the effects of the limit will be respected between {start} and {end}"
  [limiter-list start end limits]
  (let [before (filter #(.isBefore (:time %) start) limiter-list)
        between (filter #(between? (:time %) start end) limiter-list)
        after (filter #(.isAfter (:time %) end) limiter-list)
        before-end (last (filter #(.isBefore (:time %) end) limiter-list))]
    (concat
      before [(assoc limits :time start)]
      (map #(add-limits-to-limiter % limits) between)
      [(assoc before-end :time end)] after)))

(defn add-limits
  "adds a limiter to the limiters object"
  [limiters start end limits]
  (let [limiter-list (remove-nil (concat [(:prev limiters)] (:next limiters)))
        limiter-list-upd (apply-limits-to-list limiter-list start end limits)]
    {:next limiter-list-upd}))

(defn move-to-asap [limiters time-now]
  (let [removed (filter #(.isBefore (:time %) time-now) (:next limiters))
        remaining (filter #(not (.isBefore (:time %) time-now)) (:next limiters))
        asap (last removed)]
    {:asap asap
     :prev asap
     :next remaining}))

(defn mins [x] (.plusMinutes (LocalDateTime/now) x))
(defn arbitrary [mins])
