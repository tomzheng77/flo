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
  (let [db-uri "datomic:dev://localhost:4334/flo-ace-testbed"
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
  '[:find ?name ?new-time ?upd-time ?content
    :where
    [?e :note/name ?name ?tx1]
    [?e :note/content ?content ?tx2]
    [?tx1 :db/txInstant ?new-time]
    [?tx2 :db/txInstant ?upd-time]])

(defn note-q [name]
  `[:find ?new-time ?upd-time ?content
    :where
    [?e :note/name ~name ?tx1]
    [?e :note/content ?content ?tx2]
    [?tx1 :db/txInstant ?new-time]
    [?tx2 :db/txInstant ?upd-time]])

(defn get-all-notes []
  (let [db (d/db (get-conn))]
    (for [[name time-created time-updated content] (d/q (all-notes-q) db)]
      {:name name
       :time-created (if time-created (.getTime time-created))
       :time-updated (if time-updated (.getTime time-updated))
       :length (count content)
       :content (if content (nippy/thaw content))})))

(defn get-note
  ([name] (get-note name (d/db (get-conn))))
  ([name db]
   (let [[time-created time-updated content] (first (d/q (note-q name) db))]
     {:name name
      :time-created (if time-created (.getTime time-created))
      :time-updated (if time-updated (.getTime time-updated))
      :length (count content)
      :content (if content (nippy/thaw content))})))

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
      (if updated (get-note name db)))))

(defn set-note [name content]
  (d/transact-async (get-conn) [{:note/name name :note/content (nippy/freeze content)}]))

(defn new-user [email password]
  (d/transact-async (get-conn) [{:user/email email :user/password (hash-password password)}]))
