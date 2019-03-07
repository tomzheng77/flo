(ns sayaka.state
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [sayaka.constants :as c]
            [sayaka.restrictions :as r]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]])
  (:import (java.io PushbackReader)
           (org.apache.commons.io FileUtils)))

(def initial-state
  {:root           true
   :queue          []
   :proxy-settings {:transparent true}
   :last           nil
   :invalid        false})

(defn read-state []
  (try
    (with-open [r (io/reader c/primary-db :encoding c/encoding)]
      (edn/read (new PushbackReader r)))
    (catch Exception e
      (do (fatal e) (r/recover)))))

(defn write-state [state]
  (FileUtils/writeStringToFile
    (io/file c/primary-db)
    (pr-str state)
    (str c/encoding)))
