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

(def example-state {:time           (LocalDateTime/now)
                    :proxy-settings proxy/default-settings
                    :projects       #{"server365" "google-chrome"}})

(defn is-idle
  [state]
  (empty? (:next state)))

(defn proxy-settings
  [state]
  (or (-> state :previous :proxy-settings)
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
