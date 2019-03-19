(ns octavia.system.simulator-test
  (:require [clojure.test :refer :all]
            [octavia.system.simulator :as sim]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(def write-and-read
  (prop/for-all
    [filename gen/string-alphanumeric
     content gen/string-ascii]
    (sim/run :write-string filename content)
    (= content (sim/run :read-string filename))))

(testing "should read the same contents as what was written"
  (is (:pass? (tc/quick-check 20 write-and-read))))
