(ns octavia.core
  (:require [clojure.core.match :refer [match]])
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

(defn add-limiter [limiters start end limiter]
  (if (empty? (:next limiters))
    (assoc limiters :next [(assoc limiter :time start) {:time end}])))
