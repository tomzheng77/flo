(ns octavia.state
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [octavia.constants :as c]
            [octavia.restrictions :as r]
            [octavia.utils :as u]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [octavia.proxy :as proxy]
            [clojure.set :as set])
  (:import (java.io PushbackReader)
           (org.apache.commons.io FileUtils)
           (java.time LocalDateTime)))

(def initial-state
  {:next     []
   :previous nil})

(def example-state
  {:next     [{:time     (LocalDateTime/of 2019 3 9 10 0 0)
               :settings {:allow #{"google-chrome"}
                          :proxy proxy/example-settings}}
              {:time     (LocalDateTime/of 2019 3 9 11 0 0)
               :settings {:allow #{"server365" "google-chrome" "idea"}
                          :proxy proxy/example-settings}}
              {:time (LocalDateTime/of 2019 3 9 12 0 0)}]

   :previous {:time     (LocalDateTime/of 2019 3 9 8 0 0)
              :settings {:allow #{"server365" "idea"}
                         :proxy proxy/example-settings}}})

(defn satisfy-both [settings-one settings-two]
  {:allow (set/union (:allow settings-one) (:allow settings-two))
   :proxy (proxy/satisfy-both (:proxy settings-one) (:proxy settings-two))})

(defn request-between
  [state start-time end-time settings]
  (assert (instance? LocalDateTime start-time))
  (assert (instance? LocalDateTime end-time)))

(defn is-idle
  [state]
  (empty? (:next state)))

(defn proxy-settings
  [state]
  (or (-> state :previous :proxy)
      proxy/default-settings))

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
