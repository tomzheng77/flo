(ns octavia.state
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [octavia.constants :as c]
            [octavia.restrictions :as r]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [octavia.utils :as u]
            [octavia.proxy :as proxy])
  (:import (java.io PushbackReader)
           (org.apache.commons.io FileUtils)
           (java.time LocalDateTime)))

(def initial-state
  {:next     []
   :previous nil})

(def example-state
  {:next     [{:time     (LocalDateTime/of 2019 3 9 10 0 0)
               :projects #{"server365" "google-chrome" "idea"}
               :proxy    proxy/example-settings}
              {:time     (LocalDateTime/of 2019 3 9 12 0 0)
               :projects #{"server365" "google-chrome"}
               :proxy    proxy/example-settings}
              {:time (LocalDateTime/of 2019 3 9 13 30 0)}]

   :previous {:time     (LocalDateTime/of 2019 3 9 8 0 0)
              :projects #{"google-chrome"}
              :proxy    proxy/example-settings}})

(defn is-idle
  [state]
  (empty? (:next state)))

(defn proxy-settings
  [state]
  (or (-> state :previous :proxy)
      (proxy/default-settings)))

(defn read-state
  []
  (try
    (with-open [r (io/reader c/primary-db :encoding c/encoding)]
      (edn/read (new PushbackReader r)))
    (catch Exception e
      (do (fatal e) (r/recover) initial-state))))

(defn write-state
  [state]
  (u/mkdirs c/home)
  (FileUtils/writeStringToFile
    (io/file c/primary-db)
    (pr-str state)
    (str c/encoding)))
