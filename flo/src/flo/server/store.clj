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
            [datomic.api :as d]
            [flo.server.codec :refer [base64-encode hash-password]])
  (:import (java.time LocalDateTime ZoneId)
           (java.util Date)))

(def schema [{:db/ident       :user/email
              :db/unique      :db.unique/identity
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc         "email"}
             {:db/ident       :user/password
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc         "password hashed in PBKDF2 and serialized in base64"}
             {:db/ident       :user/notes
              :db/valueType   :db.type/ref
              :db/cardinality :db.cardinality/many
              :db/doc         "all of the notes owned by the user"}
             {:db/ident       :note/name
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

(defn new-user [email password]
  (d/transact-async (get-conn) [{:user/email email :user/password (hash-password password)}]))
