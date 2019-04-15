(ns flo.server.datomic
  (:require [datomic.api :as d]))

(def db-uri "datomic:dev://localhost:4334/hello")

(def -main [& args]
  (d/create-database db-uri))
