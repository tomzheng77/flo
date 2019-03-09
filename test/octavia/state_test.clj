(ns octavia.state-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop])
  (:gen-class))

(def sort-idempotent-prop
  (prop/for-all
    [v (gen/vector gen/int)]
    (= (sort v)
       (sort (sort v)))))

(testing "Arithmetic"
  (testing "with positive integers"
    (is (= 4 (+ 2 2)))
    (is (= 7 (+ 3 4))))
  (testing "with negative integers"
    (is (= -4 (+ -2 -21)))
    (is (= -1 (+ 3 -4)))))

(tc/quick-check 100 sort-idempotent-prop)
