(ns octavia.limiter-test
  (:require [clojure.test :refer :all])
  (:require [octavia.limiter :refer :all])
  (:import (java.time LocalDateTime)))

(def t0 (LocalDateTime/of 2018 11 19 0 0 0))
(defn t [x] (.plusMinutes t0 x))

(testing "remove-duplicate"
  (is (= (remove-duplicate [{:time (t 0)} {:time (t 10)} {:time (t 20)}]) [{:time (t 0)} {:time (t 20)}]))
  (is (= (remove-duplicate
           (concat (repeat 3 {:time (t 1)})
                   (repeat 5 {:time (t 2)})
                   (repeat 7 {:time (t 3)})))
         [{:time (t 1)}
          {:time (t 3)}]))
  (is (= (remove-duplicate
           (concat (repeat 3 {:time (t 1) :block-host #{"A"}})
                   (repeat 5 {:time (t 2) :block-host #{"B"}})
                   (repeat 1 {:time (t 3)})))
         [{:time (t 1) :block-host #{"A"}}
          {:time (t 2) :block-host #{"B"}}
          {:time (t 3)}])))

(testing "add-limiter"
  (is (= (add-limiter [] (t 0) (t 10) nil)
         [{:time (t 0)} {:time (t 10)}]))
  (is (= (-> nil
             (add-limiter (t 10) (t 20) {:block-host #{"A"}})
             (add-limiter (t 0) (t 30) {:block-host #{"B"}}))
         [{:time (t 0) :block-host #{"B"}}
          {:time (t 10) :block-host #{"A" "B"}}
          {:time (t 20) :block-host #{"B"}}
          {:time (t 30)}]))
  (is (= (-> nil
             (add-limiter (t 20) (t 30) {:block-host #{"A"}})
             (add-limiter (t 10) (t 40) {:block-host #{"B"}})
             (add-limiter (t 0) (t 50) {:block-host #{"C"}}))
         [{:time (t 0) :block-host #{"C"}}
          {:time (t 10) :block-host #{"C" "B"}}
          {:time (t 20) :block-host #{"C" "B" "A"}}
          {:time (t 30) :block-host #{"C" "B"}}
          {:time (t 40) :block-host #{"C"}}
          {:time (t 50)}]))
  (is (= (-> nil
             (add-limiter (t 20) (t 30) {:block-host #{"A"}})
             (add-limiter (t 10) (t 40) {:block-host #{"B"}})
             (add-limiter (t 10) (t 40) {:block-host #{"B"}})
             (add-limiter (t 10) (t 40) {:block-host #{"B"}})
             (add-limiter (t 0) (t 50) {:block-host #{"C"}})
             (add-limiter (t 0) (t 50) {:block-host #{"C"}}))
         [{:time (t 0) :block-host #{"C"}}
          {:time (t 10) :block-host #{"C" "B"}}
          {:time (t 20) :block-host #{"C" "B" "A"}}
          {:time (t 30) :block-host #{"C" "B"}}
          {:time (t 40) :block-host #{"C"}}
          {:time (t 50)}]))
  (is (= (-> nil
             (add-limiter (t 20) (t 30) {:block-host #{"A"}})
             (add-limiter (t 10) (t 40) {:block-host #{"B"}})
             (add-limiter (t 10) (t 40) {:block-host #{"B"}})
             (add-limiter (t 10) (t 40) {:block-host #{"B"}})
             (add-limiter (t 0) (t 50) {:block-host #{"C"}})
             (add-limiter (t 0) (t 50) {:block-host #{"C"}})
             (drop-before (t 30)))
         [{:time (t 20) :block-host #{"C" "B" "A"}}
          {:time (t 30) :block-host #{"C" "B"}}
          {:time (t 40) :block-host #{"C"}}
          {:time (t 50)}])))
