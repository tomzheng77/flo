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
               :settings {:restrict  #{"google-chrome"}
                          :blacklist proxy/example-settings}}
              {:time     (LocalDateTime/of 2019 3 9 11 0 0)
               :settings {:restrict  #{"server365" "google-chrome" "idea"}
                          :blacklist proxy/example-settings}}
              {:time (LocalDateTime/of 2019 3 9 12 0 0)}]

   :previous {:time     (LocalDateTime/of 2019 3 9 8 0 0)
              :settings {:restrict  #{"server365" "idea"}
                         :blacklist proxy/example-settings}}})

(defn intersect [settings-one settings-two]
  {:restrict  (set/union (u/to-set (:restrict settings-one))
                         (u/to-set (:restrict settings-two)))
   :blacklist (proxy/union (:blacklist settings-one)
                           (:blacklist settings-two))})

(defn request-between
  [state start-time end-time settings]
  (assert (instance? LocalDateTime start-time))
  (assert (instance? LocalDateTime end-time)))

(defn is-superuser?
  [state]
  (empty? (:next state)))

(defn no-restrictions?
  [state]
  (let [settings (-> state :previous :settings)]
    (and (empty? (:restrict settings))
         (proxy/no-restrictions? (:blacklist settings)))))

(defn proxy-settings
  [state]
  (or (-> state :previous :blacklist)
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
