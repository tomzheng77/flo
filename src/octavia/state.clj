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


(def maps {:at       (LocalDateTime/now)
           :type     "profile"
           :proxy    proxy/default-settings
           :projects #{"server365"}})

(def initial-state
  {:next     []
   :previous nil})

(defn is-idle
  [state]
  (or (nil? (:last state))
      (= (-> state :last :type) "unlock")))

(defn proxy-settings
  [state]
  (or (-> state :last :proxy)
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

(defn set-of [item-or-coll]
  (if (coll? item-or-coll)
    (set item-or-coll)
    #{item-or-coll}))
