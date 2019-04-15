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

(def conn (atom nil))

(defn connect []
  (let [db-uri "datomic:dev://localhost:4334/flo"]
    (d/create-database db-uri)
    (reset! conn (d/connect db-uri))))

(defn get-conn []
  (if (nil? @conn) (connect) @conn))

(defn init-schema []
  (let [schema [{:db/ident       :note/name
                 :db/unique      :db.unique/identity
                 :db/valueType   :db.type/string
                 :db/cardinality :db.cardinality/one
                 :db/doc         "unique name of the note"}
                {:db/ident       :note/content
                 :db/valueType   :db.type/bytes
                 :db/cardinality :db.cardinality/one
                 :db/doc         "nippy serialized delta format"}]]
    (d/transact (get-conn) schema)))

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

(defn get-all-notes []
  (let [db (d/db (get-conn))]
    (d/q (all-notes-q) db)))

(defn get-note
  ([name] (get-note name (d/db (get-conn))))
  ([name db]
   (let [content-raw (ffirst (d/q (note-content-q name) db))]
     (if content-raw (nippy/thaw content-raw)))))

; Date in = new Date();
; LocalDateTime ldt = LocalDateTime.ofInstant(in.toInstant(), ZoneId.systemDefault());
; Date out = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
(defn ldt-to-date [ldt]
  (cond
    (instance? LocalDateTime ldt)
    (Date/from (.toInstant (.atZone ldt (ZoneId/systemDefault))))
    (instance? Date ldt) ldt))

(defn get-note-at [name at]
  (let [date (ldt-to-date at)]
    (assert (not (nil? date)))
    (get-note name (d/as-of (d/db (get-conn)) date))))

(defn get-note-created [name]
  (let [db (d/db (get-conn))]
    (ffirst (d/q (note-created-q name) db))))

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
