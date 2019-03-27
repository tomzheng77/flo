(ns limiter.limiter-test
  (:require [clojure.test :refer :all]
            [limiter.limiter :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop])
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
      (let [limits (first limits-vec)]
        (recur (add-limiter limiters (:start limits) (:end limits) limits) (next list))))))

(defmacro forall [seq-exprs body-expr]
  `(let [result# (for ~seq-exprs ~body-expr)]
     (every? identity result#)))

(defn every-minute [start end]
  (loop [at start out []]
    (if-not (.isBefore at end)
      out
      (recur (.plusMinutes at 1)
             (conj out at)))))

(def double-limits-prop
  (prop/for-all
    [limits-vec gen-limits-vec]
    (= (with-limits limits-vec)
       (with-limits
         (concat limits-vec limits-vec)))))

(def encapsulate-prop
  (prop/for-all
    [limits-vec gen-limits-vec]
    (let [limiters (with-limits limits-vec)]
      (forall [limits limits-vec]
        (let [start (:start limits) end (:end limits)]
          (forall [at (every-minute start end)]
            (includes? (limiter-at limiters at) limits)))))))

(def always-end-prop
  (prop/for-all
    [limits-vec gen-limits-vec]
    (= true (:is-last (limiter-at (with-limits limits-vec) (t 2005))))))

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
  (is (= true (:result (tc/quick-check 50 double-limits-prop))))
  (is (= true (:result (tc/quick-check 50 always-end-prop))))
  (is (= true (:result (tc/quick-check 50 encapsulate-prop)))))
