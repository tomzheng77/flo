(ns octavia.state-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [octavia.utils :as util]
            [octavia.state :as st]
            [octavia.proxy :as proxy])
  (:gen-class))

(def sort-idempotent-prop
  (prop/for-all
    [v (gen/vector gen/int)]
    (= (sort v)
       (sort (sort v)))))

(testing "state"
  (testing "should start as idle"
    (is (= true (st/is-idle st/initial-state))))
  (testing "should start with default proxy settings"
    (is (= proxy/default-settings (st/proxy-settings st/initial-state)))))

(testing "Arithmetic"
  (testing "with positive integers"
    (is (= 4 (+ 2 2)))
    (is (= 7 (+ 3 4))))
  (testing "with negative integers"
    (is (= -4 (+ -2 -2)))
    (is (= -1 (+ 3 -4)))))

(tc/quick-check 100 sort-idempotent-prop)
