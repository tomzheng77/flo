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
            [taoensso.timbre :as timbre :refer [trace debug info error]]
            [datomic.api :as d])
  (:import (java.io ByteArrayOutputStream)
           (java.time LocalDateTime ZoneId)
           (java.util Date)))

(def schema [{:db/ident       :note/name
              :db/unique      :db.unique/identity
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc         "unique name of the note"}
             {:db/ident       :note/content
              :db/valueType   :db.type/bytes
              :db/cardinality :db.cardinality/one
              :db/doc         "nippy serialized content"}])

; connections are long lived and cached by d/connect
; hence there is no need to store the connection
(def get-conn
  (let [db-uri "datomic:dev://localhost:4334/flo-ace"
        started (atom false)]
    (fn []
      ; when this function is called for the first time
      ; check if the database has been created
      (locking started
        (when (not @started)
          (reset! started true)
          (when (d/create-database db-uri)
            ; the database has just been created
            ; initialize the schema
            (d/transact (d/connect db-uri) schema))))
      (d/connect db-uri))))

(defn all-notes-q []
  '[:find ?e ?name ?content
    :where
    [?e :note/name ?name]
    [?e :note/content ?content]])

(defn note-content-q [name]
  `[:find ?content
    :where
    [?e :note/name ~name]
    [?e :note/content ?content]])

(defn note-created-q [name]
  `[:find ?tx-time
    :where
    [?e :note/name ~name ?tx]
    [?tx :db/txInstant ?tx-time]])

(defn note-updated-q [name]
  `[:find ?tx-time
    :where
    [?e :note/name ~name]
    [?e :note/content ?content ?tx]
    [?tx :db/txInstant ?tx-time]])

(defn get-all-notes []
  (let [db (d/db (get-conn))]
    (d/q (all-notes-q) db)))

(defn get-note-content
  ([name] (get-note-content name (d/db (get-conn))))
  ([name db]
   (let [content-raw (ffirst (d/q (note-content-q name) db))]
     (if content-raw (nippy/thaw content-raw)))))

(defn get-note-created
  ([name] (get-note-created name (d/db (get-conn))))
  ([name db] (ffirst (d/q (note-created-q name) db))))

(defn get-note-updated
  ([name] (get-note-updated name (d/db (get-conn))))
  ([name db] (ffirst (d/q (note-updated-q name) db))))

; converts to java.util.Date
(defn to-util-date [ldt]
  (cond
    (instance? Long ldt) (new Date ldt)
    (instance? Integer ldt) (new Date ldt)
    (instance? LocalDateTime ldt)
    (Date/from (.toInstant (.atZone ldt (ZoneId/systemDefault))))
    (instance? Date ldt) ldt))

(defn get-note-at [name at]
  (let [date (to-util-date at)]
    (assert (not (nil? date)))
    (let [db (d/as-of (d/db (get-conn)) date)
          updated (get-note-updated name db)]
      (if updated {:content (get-note-content name db) :updated updated}))))

(defn set-note [name content]
  (d/transact-async (get-conn) [{:note/name name :note/content (nippy/freeze content)}]))

(defn file->bytes [file]
  (with-open [in (io/input-stream file)
              out (new ByteArrayOutputStream)]
    (io/copy in out)
    (.toByteArray out)))

(defn load-store []
  (let [store-dir (io/file "store")
        nippy-suffix ".nippy"
        files (.listFiles (io/file store-dir))]
    (doseq [file files]
      (when (str/ends-with? (.getName file) nippy-suffix)
        (let [content (file->bytes file)
              filename (.getName file)
              name (subs filename 0 (- (count filename) (count nippy-suffix)))]
          (debug "loading" name "from store")
          (d/transact-async (get-conn) [{:note/name name :note/content content}]))))))
