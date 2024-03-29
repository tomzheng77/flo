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
            [flo.server.global :as global]
            [flo.server.codec :refer [base64-encode hash-password]])
  (:import (java.time LocalDateTime ZoneId LocalDate LocalTime)
           (java.util Date)
           (java.time.format DateTimeFormatter)))

(def schema [{:db/ident       :note/name
              :db/unique      :db.unique/identity
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc         "unique name of the note"}
             {:db/ident       :note/content
              :db/valueType   :db.type/bytes
              :db/cardinality :db.cardinality/one
              :db/doc         "nippy serialized content"}
             {:db/ident       :blob/hash
              :db/unique      :db.unique/identity
              :db/valueType   :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc         "sha1 hash of a blob"}
             {:db/ident       :blob/buffer
              :db/valueType   :db.type/bytes
              :db/cardinality :db.cardinality/one
              :db/doc         "byte buffer of its content"}])

; connections are long lived and cached by d/connect
; hence there is no need to store the connection
(def started-dbs (atom #{})) ; set of names of databases that have been started
(defn get-conn []
  (let [db-name @global/db-name
        db-uri (str "datomic:dev://database:4334/" db-name "?user=datomic&password=peer-pwd")] ; HACK(tom): wire up to database docker
    ; when this function is called for the first time
    ; check if the database has been created
    (locking started-dbs
      (when (not (@started-dbs db-name))
        (swap! started-dbs #(conj % db-name))
        (when (d/create-database db-uri)
          ; the database has just been created
          ; initialize the schema
          (d/transact (d/connect db-uri) schema))))
    (d/connect db-uri)))


; migration script to add blobs to flo-ace
; (let [db-name "flo-ace"
;       db-uri (str "datomic:dev://localhost:4334/" db-name)]
;     (d/transact
;       (d/connect db-uri)
;       [{:db/ident       :blob/hash
;              :db/unique      :db.unique/identity
;              :db/valueType   :db.type/string
;              :db/cardinality :db.cardinality/one
;              :db/doc         "sha1 hash of a blob"}
;        {:db/ident       :blob/buffer
;         :db/valueType   :db.type/bytes
;         :db/cardinality :db.cardinality/one
;         :db/doc         "byte buffer of its content"}]))

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

(defn blob-q [hash]
  `[:find ?new-time ?buffer
    :where
    [?e :blob/hash ~hash ?tx1]
    [?e :blob/buffer ?buffer ?tx2]
    [?tx1 :db/txInstant ?new-time]])

(def date-regex (re-pattern #"[0-9]{1,2} (jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec) [0-9]{4}"))
(def date-range-regex (re-pattern #"[0-9]{1,2} (jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec) [0-9]{4} to [0-9]{1,2} (jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec) [0-9]{4}"))

; converts a string to a `LocalDateTime` or `LocalDate`
(defn parse-date [in]
  (let [date-format-1 (DateTimeFormatter/ofPattern "yyyy-MM-dd")
        date-format-2 (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm:ss")
        date-format-3 (DateTimeFormatter/ofPattern "yyyy-MM-dd'T'HH:mm")
        date-format-4 (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")
        date-format-5 (DateTimeFormatter/ofPattern "d MMM yyyy")]
    (cond
      (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2}" in)
      (LocalDate/parse in date-format-1)
      (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}" in)
      (LocalDateTime/parse in date-format-2)
      (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}" in)
      (LocalDateTime/parse in date-format-3)
      (re-matches #"[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}" in)
      (LocalDateTime/parse in date-format-4)
      (re-matches date-regex in)
      (let [[a b c] (str/split in #" ")
            upper (str (str/upper-case (subs b 0 1)) (subs b 1))]
        (LocalDate/parse (str a " " upper " " c) date-format-5)))))

; converts to java.util.Date
(defn to-util-date [ldt]
  (cond
    (instance? Long ldt) (new Date ldt)
    (instance? Integer ldt) (new Date ldt)
    (string? ldt)
    (cond
      (re-matches #"[0-9]+" ldt) (Long/parseLong ldt)
      true (to-util-date (parse-date ldt)))

    (instance? LocalDate ldt) (to-util-date (LocalDateTime/of ldt (LocalTime/now)))
    (instance? LocalTime ldt) (to-util-date (LocalDateTime/of (LocalDate/now) ldt))
    (instance? LocalDateTime ldt)
    (Date/from (.toInstant (.atZone ldt (ZoneId/systemDefault))))
    (instance? Date ldt) ldt))

(defn get-all-notes
  ([] (get-all-notes (d/db (get-conn)) 0))
  ([at]
   (let [date (to-util-date at)]
     (assert (not (nil? date)))
     (let [db (d/as-of (d/db (get-conn)) date)]
       (get-all-notes db 0))))
  ([db _]
   (for [[name time-created time-updated content-raw] (d/q (all-notes-q) db)]
     (let [content (if content-raw (nippy/thaw content-raw))]
       {:name name
        :time-created (if time-created (.getTime time-created))
        :time-updated (if time-updated (.getTime time-updated))
        :content content}))))

(defn get-note
  ([name] (get-note name (d/db (get-conn))))
  ([name db]
   (let [[time-created time-updated content-raw] (first (d/q (note-q name) db))]
     (let [content (if content-raw (nippy/thaw content-raw))]
       {:name name
        :time-created (if time-created (.getTime time-created))
        :time-updated (if time-updated (.getTime time-updated))
        :content content}))))

(defn get-note-at [name at]
  (let [date (to-util-date at)]
    (assert (not (nil? date)))
    (let [db (d/as-of (d/db (get-conn)) date)]
      (get-note name db))))

(defn set-note [name content]
  (d/transact-async (get-conn) [{:note/name name :note/content (nippy/freeze content)}]))

(defn set-blob [hash buffer]
  (d/transact-async (get-conn) [{:blob/hash hash :blob/buffer buffer}]))

(defn get-blob
  ([hash] (get-blob hash (d/db (get-conn))))
  ([hash db]
   (let [[time-created buffer] (first (d/q (blob-q hash) db))]
     {:hash hash
      :time-created (if time-created (.getTime time-created))
      :buffer buffer})))
