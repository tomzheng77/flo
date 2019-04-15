(ns flo.server.datomic-example
  (:require [datomic.api :as d])
  (:import (java.util Date UUID)))

(comment
  (def db-uri "datomic:dev://localhost:4334/example")
  (d/delete-database db-uri)
  (d/create-database db-uri)
  (def conn (d/connect db-uri))

  ; create the note schema
  (def note-schema [{:db/ident       :note/name
                     :db/unique      :db.unique/identity
                     :db/valueType   :db.type/string
                     :db/cardinality :db.cardinality/one
                     :db/doc         "unique name of the note"}
                    {:db/ident       :note/time-created
                     :db/valueType   :db.type/instant
                     :db/cardinality :db.cardinality/one
                     :db/doc         "time when the note was first created"}
                    {:db/ident       :note/content
                     :db/valueType   :db.type/bytes
                     :db/cardinality :db.cardinality/one
                     :db/doc         "nippy serialized delta format"}])

  (println "ins schema" @(d/transact conn note-schema))
  (println "ins schema" @(d/transact conn note-schema))
  (println "ins schema" @(d/transact conn note-schema))

  (time (dotimes [i 10000]
          (d/transact-async conn [{:note/name         (.toString (UUID/randomUUID))
                                   :note/time-created (new Date)
                                   :note/content      (.getBytes "content" "UTF-8")}])))

  (def all-notes-q '[:find ?e ?name ?time-created ?content
                     :where
                     [?e :note/name ?name]
                     [?e :note/time-created ?time-created]
                     [?e :note/content ?content]])

  (def db (d/db conn))
  (println (count (d/q all-notes-q db))))
