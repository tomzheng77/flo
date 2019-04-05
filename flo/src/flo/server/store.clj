(ns flo.server.store
  (:require [clojure.core.match :refer [match]]
            [clojure.pprint :refer [pprint]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.anti-forgery :as anti-forgery :refer [wrap-anti-forgery]]
            [clojure.core.async :as async :refer [chan <! <!! >! >!! put! chan go go-loop]]
            [clojure.set :as set]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as timbre :refer [trace debug info error]]))

; the API of this module
; keys should be strings, values should be serializable with pr-str
; it will be read upon startup, writes are automatic
(def store (atom {}))

(def store-dir (io/file "store"))
(def suffix ".nippy")

(.mkdirs store-dir)
(let [files (.listFiles (io/file store-dir))]
  (reset! store {})
  (doseq [file files]
    (when (str/ends-with? (.getName file) suffix)
      (let [contents (nippy/thaw-from-file file)
            filename (.getName file)
            name (subs filename 0 (- (count filename) (count suffix)))]
        (debug "loading" name "from store")
        (swap! store #(assoc % name contents))))))

; this will start a thread which continuously writes
; the latest version of the store
(let [store-last-write (atom {})
      signals (chan)
      signal-count (atom 0)]
  ; whenever the value of contents changes, add a new signal
  ; the signal can be any value
  (add-watch store :save-store
    (fn [_ _ _ _]
      (when (> 512 @signal-count)
        (swap! signal-count inc)
        (>!! signals 0))))
  (go-loop []
    (let [_ (<! signals)]
      (swap! signal-count dec)
      (let [now-store @store [_ changed _] (diff @store-last-write now-store)]
        (doseq [[name contents] changed]
          (let [file (io/file store-dir (str name suffix))]
            (nippy/freeze-to-file file contents)
            (debug "written" name "to store, len =" (.length file))))
        (reset! store-last-write now-store)))
    (recur)))
