(ns octavia.core
  (:import (java.time LocalDateTime)))

; a single limiter, a limiter updates the profile of the user
; time - the time for this limiter to occur
; block-login - whether the user should be barred from login entirely (defaults to false)
; block-website - blocks any hosts which matches one of the strings (defaults to none)
; block-project - blocks any projects which matches the name precisely (defaults to none)
{:time          (LocalDateTime/now)
 :block-login   false
 :block-website #{"www.google.com" "anime" "manga"}
 :block-project #{"clojure365"}}

; the previous and next limiters
; the last limiter will always be treated as an unlock
{:prev nil
 :next [{:time          (LocalDateTime/now)
         :block-login   false
         :block-website #{"www.google.com" "anime" "manga"}
         :block-project #{"clojure365"}}
        {:time          (LocalDateTime/now)
         :block-login   false
         :block-website #{"www.google.com" "anime" "manga"}
         :block-project #{"clojure365"}}]}
