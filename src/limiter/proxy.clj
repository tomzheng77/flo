(ns limiter.proxy
  (:require [limiter.constants :as c]
            [taoensso.timbre :as timbre]
            [clojure.string :as str])
  (:import (org.littleshoot.proxy.impl DefaultHttpProxyServer)
           (org.littleshoot.proxy HttpFiltersSourceAdapter HttpFiltersAdapter)
           (io.netty.handler.codec.http DefaultFullHttpResponse HttpVersion HttpResponseStatus)))

(def server (atom nil))
(def block-host (atom #{}))

(defn unauthorized [] (new DefaultFullHttpResponse
                           (HttpVersion/HTTP_1_1)
                           (HttpResponseStatus/UNAUTHORIZED)))

(defn filters-source
  "wraps a (HttpRequest, HttpObject) => HttpObject filter inside
  a HttpFiltersSource instance"
  [filter]
  (proxy [HttpFiltersSourceAdapter] []
    (filterRequest [request ctx]
      (proxy [HttpFiltersAdapter] [request]
        (serverToProxyResponse [response]
          (filter request response))))))

(defn start-transparent []
  "starts a HttpProxyServer instance with mitm filter disabled
  the instance is returned at the end"
  (-> (DefaultHttpProxyServer/bootstrap)
      (.withPort c/proxy-port)
      (.withAllowLocalOnly true)
      (.withTransparent true)
      (.withFiltersSource
        (filters-source
          (fn [request response]
            (let [host (.get (.headers request) "Host")]
              (if (not-any? #(str/includes? host %) @block-host)
                (unauthorized) response)))))
      (.start)))

(defn start-server
  "starts or restarts the server"
  []
  (locking server
    (if @server (.stop @server))
    (reset! server (start-transparent))))
