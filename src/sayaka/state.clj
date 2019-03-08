(ns sayaka.state
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [sayaka.constants :as c]
            [sayaka.restrictions :as r]
            [taoensso.timbre :as timbre
             :refer [log trace debug info warn error fatal report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]
            [sayaka.utils :as u]
            [sayaka.proxy :as proxy])
  (:import (java.io PushbackReader)
           (org.apache.commons.io FileUtils)
           (java.time LocalDateTime)))

{:at (LocalDateTime/now)
 :proxy (proxy/default-settings)
 :projects #{"server365"}
 }

(def initial-state
  {:next     []
   :previous nil})

(defn is-super
  [state]
  (or (nil? (:last state))
      (= (-> state :last :action) "unlock")))

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
