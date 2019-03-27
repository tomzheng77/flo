(ns limiter.limiter-test
  (:require [clojure.test :refer :all]
            [limiter.limiter :refer :all]
            [clojure.test.check :as tc]
            [clojure.pprint :refer [pprint]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop])
  (:import (java.time LocalDateTime)))

(def t0 (LocalDateTime/of 2018 11 19 0 0 0))
(defn t [x] (.plusMinutes t0 x))

(def gen-limits
  (gen/fmap (fn [[t0 t1 block-login block-host block-folder]]
              {:start        (t t0)
               :end          (t (+ t0 t1))
               :block-login  block-login
               :block-host   block-host
               :block-folder block-folder})
            (gen/tuple
              (gen/choose 1 1000)
              (gen/choose 1 1000)
              gen/boolean
              (gen/set gen/string-alphanumeric {:max-elements 5})
              (gen/set gen/string-alphanumeric {:max-elements 5}))))

(def gen-limits-vec (gen/vector gen-limits))
(defn with-limits [limits-vec]
  (loop [limiters nil list limits-vec]
    (if (empty? list)
      limiters
      (let [limits (first list)]
        (recur (add-limiter limiters (:start limits) (:end limits) limits) (next list))))))

(defmacro forall [seq-exprs body-expr]
  `(let [result# (for ~seq-exprs ~body-expr)]
     (every? identity result#)))

(defn every-step
  ([start end] (every-step start end 1))
  ([start end step]
   (assert (integer? step))
   (assert (< 0 step))
   (loop [at start out []]
     (if-not (.isBefore at end)
       out
       (recur (.plusMinutes at step)
              (conj out at))))))

(def prop-add-limiters-twice
  (prop/for-all
    [limits-vec gen-limits-vec]
    (= (with-limits limits-vec)
       (with-limits
         (concat limits-vec limits-vec)))))

(def prop-encapsulate
  (prop/for-all
    [limits-vec gen-limits-vec]
    (let [limiters (with-limits limits-vec)]
      (forall [limits limits-vec]
        (let [start (:start limits) end (:end limits)
              item (-> limits
                       (dissoc :start)
                       (dissoc :end))]
          (forall [at (every-step start end 10)]
            (includes? (limiter-at limiters at) item)))))))

; regardless what limits are added, :is-last should always be true
; after the last limiter
(def prop-always-end
  (prop/for-all
    [limits-vec gen-limits-vec]
    (= true (:is-last (limiter-at (with-limits limits-vec) (t 2005))))))

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

(testing "every-minute"
  (is (= 100 (count (every-step (t 100) (t 200)))))
  (is (= 27 (count (every-step (t 27) (t 54))))))

(testing "add-limiter"
  (is (= {:is-last true} (limiter-at nil (t 10))))
  (is (= [] (drop-before nil (t 0))))
  (is (= [] (drop-before [nil] (t 0))))
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
         [{:time (t 30) :block-host #{"C" "B"}}
          {:time (t 40) :block-host #{"C"}}
          {:time (t 50)}]))
  (let [example (-> nil
                    (add-limiter (t 20) (t 30) {:block-host #{"A"}})
                    (add-limiter (t 10) (t 40) {:block-host #{"B"}})
                    (add-limiter (t 0) (t 50) {:block-host #{"C"}}))]
    (is (= {:time (t 0) :block-host #{"C"} :is-last false} (limiter-at example (t 5))))
    (is (= {:time (t 10) :block-host #{"B" "C"} :is-last false} (limiter-at example (t 10))))
    (is (= {:time (t 20) :block-host #{"A" "B" "C"} :is-last false} (limiter-at example (t 20))))
    (is (= {:time (t 50) :is-last true} (limiter-at example (t 60)))))
  (let [example (-> nil
                    (add-limiter (t 0) (t 400) nil)
                    (add-limiter (t 100) (t 110) {:block-login true})
                    (add-limiter (t 200) (t 210) {:block-login true})
                    (add-limiter (t 300) (t 310) {:block-login true}))]
    (is (= true (:block-login (limiter-at example (t 105)))))
    (is (= true (:block-login (limiter-at example (t 205)))))
    (is (= true (:block-login (limiter-at example (t 305)))))
    (is (= false (true? (:block-login (limiter-at example (t 350))))))
    (is (= (drop-before example (t 205))
           [{:time (t 200) :block-login true}
            {:time (t 210)}
            {:time (t 300) :block-login true}
            {:time (t 310)}
            {:time (t 400)}])))
  (let [example (-> nil
                    (add-limiter (t 0) (t 400) nil)
                    (add-limiter (t 100) (t 110) {:block-login true})
                    (add-limiter (t 200) (t 210) {:block-login true})
                    (add-limiter (t 300) (t 310) {:block-login true})
                    (add-limiter (t 100) (t 110) {:block-login true})
                    (add-limiter (t 200) (t 210) {:block-login true})
                    (add-limiter (t 300) (t 310) {:block-login true}))]
    (is (= true (:block-login (limiter-at example (t 105)))))
    (is (= true (:block-login (limiter-at example (t 205)))))
    (is (= true (:block-login (limiter-at example (t 305)))))
    (is (= false (true? (:block-login (limiter-at example (t 350))))))
    (is (= (drop-before example (t 205))
           [{:time (t 200) :block-login true}
            {:time (t 210)}
            {:time (t 300) :block-login true}
            {:time (t 310)}
            {:time (t 400)}]))
    (is (= example
           [{:time (t 0)}
            {:time (t 100) :block-login true}
            {:time (t 110)}
            {:time (t 200) :block-login true}
            {:time (t 210)}
            {:time (t 300) :block-login true}
            {:time (t 310)}
            {:time (t 400)}])))
  (is (= [{:time         (LocalDateTime/parse "2018-11-19T05:49")
           :block-login  true,
           :block-host   #{"" "S7VL" "y6" "Q9HI"},
           :block-folder #{}}
          {:time         (LocalDateTime/parse "2018-11-19T12:17")
           :block-login  true,
           :block-host   #{"" "S7VL" "y6" "Q9HI" "PuNW" "y0" "N" "Qq"},
           :block-folder #{"Y2m"}}
          {:time         (LocalDateTime/parse "2018-11-19T16:53")
           :block-login  false,
           :block-host   #{"PuNW" "y0" "N" "Qq"},
           :block-folder #{"Y2m"}}
          {:time (LocalDateTime/parse "2018-11-19T21:59")}]
         (-> nil
             (add-limiter (LocalDateTime/parse "2018-11-19T05:49") (LocalDateTime/parse "2018-11-19T16:53")
                          {:block-login  true,
                           :block-host   #{"" "S7VL" "y6" "Q9HI"},
                           :block-folder #{}})
             (add-limiter (LocalDateTime/parse "2018-11-19T12:17") (LocalDateTime/parse "2018-11-19T21:59")
                          {:block-login  false,
                           :block-host   #{"PuNW" "y0" "N" "Qq"},
                           :block-folder #{"Y2m"}}))))
  (is (= true (:result (tc/quick-check 50 prop-add-limiters-twice))))
  (is (= true (:result (tc/quick-check 50 prop-always-end))))
  (is (= true (:result (tc/quick-check 20 prop-encapsulate)))))
