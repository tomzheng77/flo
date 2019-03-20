(ns octavia.system.simulator-test
  (:require [clojure.test :refer :all]
            [octavia.system.simulator :as sim]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(def write-and-read-basic
  (prop/for-all
    [filename gen/string-alphanumeric
     content gen/string-ascii]
    (sim/run :reset)
    (sim/run :write-string filename content)
    (= content (sim/run :read-string filename))))

(def write-and-read-structured
  (prop/for-all
    [path (gen/vector gen/string-alphanumeric)
     content gen/string-ascii]
    (sim/run :reset)
    (sim/run :write-string path content)
    (= content (sim/run :read-string path))))

(def add-groups
  (prop/for-all
    [groups (gen/vector gen/string-alphanumeric)]
    (sim/run :reset)
    (doseq [group groups] (sim/run :add-group group))
    (every? (sim/run :groups) groups)))

(testing "should read the same contents as what was written"
  (is (:pass? (tc/quick-check 20 write-and-read-basic)))
  (is (:pass? (tc/quick-check 20 write-and-read-structured)))
  (is (:pass? (tc/quick-check 20 add-groups))))
