(ns octavia.proxy
  (:require [octavia.constants :as c]
            [octavia.utils :as u]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set])
  (:import (org.littleshoot.proxy.impl DefaultHttpProxyServer)
           (org.littleshoot.proxy HttpFiltersSourceAdapter HttpFiltersAdapter)))

(def server-lock (new Object))
(def server (atom nil))
(def settings (atom {}))

(defn filters-source
  "wraps a (HttpRequest, HttpObject) => HttpObject filter inside
  a HttpFiltersSource instance"
  [filter]
  (proxy [HttpFiltersSourceAdapter] []
    (filterRequest [request ctx]
      (proxy [HttpFiltersAdapter] [request]
        (serverToProxyResponse [response]
          (filter request response))))))

(defn start-server-mitm-off []
  "starts a HttpProxyServer instance with mitm filter disabled
  the instance is returned at the end"
  (-> (DefaultHttpProxyServer/bootstrap)
      (.withPort c/proxy-port)
      (.withAllowLocalOnly true)
      (.withTransparent true)
      (.withFiltersSource
        (filters-source
          (fn [request response]
            (println request response)
            response)))
      (.start)))

(defn start-server
  "starts or restarts the server with the given settings"
  []
  (locking server-lock
    (if @server (.stop @server))
    (reset! server start-server-mitm-off)))
