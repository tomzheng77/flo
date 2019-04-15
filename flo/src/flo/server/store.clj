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
            [taoensso.timbre :as timbre :refer [trace debug info error]])
  (:import (java.util Date)
           (java.io ByteArrayOutputStream)))

(def db-uri "datomic:dev://localhost:4334/flo")
(d/create-database db-uri)
(def conn (d/connect db-uri))

(def schema
  [{:db/ident       :note/name
    :db/unique      :db.unique/identity
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "unique name of the note"}
   {:db/ident       :note/content
    :db/valueType   :db.type/bytes
    :db/cardinality :db.cardinality/one
    :db/doc         "nippy serialized delta format"}])

(d/transact conn note-schema)

(def store (atom {}))

(defn note-content-q [name]
  '[:find ?content
    :where
    [?e :note/name name]
    [?e :note/content ?content]])

(def get-note [name]
  (let [db (d/db conn)]
    (d/q (note-content-q name) db)))

(def get-note-creation [name]
  (let [db (d/db conn)]
    (d/q (note-content-q name) db)))

(def set-note [name content]
  (d/transact-async conn [{:note/name name :note/content content}]))

(defn file->bytes [file]
  (with-open [in (io/input-stream file)
              out (new ByteArrayOutputStream)]
    (io/copy in out)
    (.toByteArray out)))

(def store-dir (io/file "store"))
(let [files (.listFiles (io/file store-dir))]
  (doseq [file files]
    (when (str/ends-with? (.getName file) suffix)
      (let [content (file->bytes file)
            filename (.getName file)
            name (subs filename 0 (- (count filename) (count suffix)))]
        (debug "loading" name "from store")
        (d/transact-async conn [{:note/name name :note/content content}])))))
