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

(defn both [limiter-a limiter-b]
  {:time          (:time limiter-a)
   :block-login   (or (:block-login limiter-a) (:block-login limiter-b))
   :block-host    (set/union (:block-host limiter-a) (:block-host limiter-b))
   :block-project (set/union (:block-project limiter-a) (:block-project limiter-b))})

(defn add-limiter [limiters start end limiter]
  (if (empty? (:next limiters))
    (assoc limiters :next [(assoc limiter :time start) {:time end}])))

(defn between?
  [time start end]
  (and
    (not (.isBefore time start))
    (not (.isAfter time end))))

(defn add-limiter-to-list
  [limiter-list start end limiter]
  (let [before (filter #(.isBefore (:time %) start) limiter-list)
        between (filter #(between? (:time %) start end) limiter-list)
        after (filter #(.isAfter (:time %) end) limiter-list)]
    (concat before [limiter]
            (map #(both % limiter) between)
            [limiter] after)))
