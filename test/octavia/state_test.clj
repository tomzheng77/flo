(ns octavia.state-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [octavia.utils :as util]
            [octavia.state :as st]
            [octavia.proxy :as proxy])
  (:gen-class))

(def string-set (gen/set gen/string-alphanumeric))

(def intersect-identity
  (prop/for-all
    [a string-set b string-set c string-set]
    (let [settings {:restrict a :blacklist {:not-contain-ctype b :not-contain c}}]
      (= settings (st/intersect nil settings)))))

(def intersect-commutative
  (prop/for-all
    [a string-set b string-set c string-set
     d string-set e string-set f string-set]
    (let [settings-one {:restrict a :blacklist {:not-contain-ctype b :not-contain c}}
          settings-two {:restrict d :blacklist {:not-contain-ctype e :not-contain f}}]
      (= (st/intersect settings-one settings-two)
         (st/intersect settings-two settings-one)))))

(testing "proxy"
  (testing "should default to no restrictions"
    (is (= true (proxy/no-restrictions? proxy/default-settings)))))

(testing "state"
  (testing "should start as superuser and with no restrictions"
    (is (= true (st/is-superuser? st/initial-state)))
    (is (= true (st/no-restrictions? st/initial-state))))
  (testing "should start with default proxy settings"
    (is (= proxy/default-settings (st/proxy-settings st/initial-state))))
  (testing "should intersect nil with nil"
    (is (= true (st/no-restrictions? (st/intersect nil nil)))))
  (testing "should intersect nil with anything else"
    (is (:pass? (tc/quick-check 20 intersect-identity)))
    (is (:pass? (tc/quick-check 20 intersect-commutative)))))
