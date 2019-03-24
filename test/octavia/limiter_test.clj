(ns octavia.limiter-test
  (:require [clojure.test :refer :all])
  (:require [octavia.limiter :refer :all])
  (:import (java.time LocalDateTime)))

(def t0 (LocalDateTime/of 2018 11 19 0 0 0))
(defn t [x] (.plusMinutes t0 x))
(println (add-limiter [] (t 0) (t 10) {}))


(is (= (remove-duplicate [{:time (t 0)} {:time (t 10)} {:time (t 20)}])
       [{:time (t 0)} {:time (t 20)}]))

(is (= (add-limiter [] (t 0) (t 10) nil)
       [{:time (t 0)} {:time (t 10)}]))
