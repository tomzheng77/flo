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

(testing "proxy"
  (testing "should default to no restrictions"
    (is (= true (proxy/no-restrictions proxy/default-settings)))))

(testing "state"
  (testing "should start as idle"
    (is (= true (st/is-idle st/initial-state))))
  (testing "should start with default proxy settings"
    (is (= proxy/default-settings (st/proxy-settings st/initial-state))))
  (testing "should intersect nil with nil"
    (is (= {:restrict #{}
            :proxy    {:not-contain-ctype #{},
                       :not-contain       #{}}}
           (st/intersect nil nil))))
  (testing "should intersect nil with anything else"
    (is (= {:restrict #{}
            :proxy    {:not-contain-ctype #{"B"},
                       :not-contain       #{"E"}}}
           (st/intersect nil {:restrict #{"A"}
                              :proxy    {:not-contain-ctype #{"B"},
                                         :not-contain       #{"E"}}})))))

(testing "Arithmetic"
  (testing "with positive integers"
    (is (= 4 (+ 2 2)))
    (is (= 7 (+ 3 4))))
  (testing "with negative integers"
    (is (= -4 (+ -2 -2)))
    (is (= -1 (+ 3 -4)))))

(tc/quick-check 100 sort-idempotent-prop)
