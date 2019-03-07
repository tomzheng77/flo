(ns sayaka.proxy
  (:require [sayaka.constants :as c]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (org.littleshoot.proxy.impl DefaultHttpProxyServer)
           (net.lightbody.bmp.mitm RootCertificateGenerator KeyStoreFileCertificateSource)
           (net.lightbody.bmp.mitm.manager ImpersonatingMitmManager)
           (org.littleshoot.proxy HttpFiltersSourceAdapter HttpFiltersAdapter)
           (io.netty.handler.codec.http HttpResponse DefaultFullHttpResponse HttpVersion HttpResponseStatus HttpHeaders HttpMessage HttpRequest)
           (java.util.regex Pattern)))

(def default-settings {:transparent true})
(def example-settings {:transparent false :allow #{"www.google.com" "www.sbt.com"} :deny #{"www.youtube.com"}})

(defn mkdirs [file-path] (.mkdirs (io/file file-path)))

(defn write-certificates []
  (mkdirs c/certificate-root)
  (let [gen (.build (RootCertificateGenerator/builder))]
    (do
      (.saveRootCertificateAsPemFile gen (io/file c/certificate))
      (.savePrivateKeyAsPemFile gen (io/file c/private-key) "123456")
      (.saveRootCertificateAndKey gen "PKCS12" (io/file c/key-store) "private-key" "123456"))))

(defn find-url
  "finds the destination URL of a HttpRequest"
  [^HttpRequest request]
  (let [host (HttpHeaders/getHeader request "Host")
        uri (.getUri request)
        http-regex #"^https?://"]
    (str/replace-first
      (if (re-find http-regex uri)
        uri
        (str host uri))
      http-regex "")))

(defn find-content-type
  "finds the content type of a request or response"
  [^HttpMessage response]
  (HttpHeaders/getHeader response "Content-Type"))

(defn matches
  "tests whether the text can be matched by the pattern.
  pattern can be either string or regex.Pattern."
  [text pattern]
  (if (instance? Pattern pattern)
    (re-find pattern text)
    (str/includes? text pattern)))

(defn should-allow
  "tests whether the HTTP transaction should be allowed.
  depends on the value of settings-atom"
  [request response settings]
  (let [url (find-url request)
        content-type (find-content-type response)]
    (or
      (not (str/includes? "text/html" content-type))
      (and
        (some (partial matches url) (:allow settings))
        (some (partial matches url) (:deny settings))))))

(def server-lock (new Object))
(def server-atom (atom nil))
(def settings-atom (atom nil))

(defn start-server-mitm-off []
  "starts a HttpProxyServer instance with mitm filter disabled
  the instance is returned at the end"
  (-> (DefaultHttpProxyServer/bootstrap)
      (.withPort c/proxy-port)
      (.withAllowLocalOnly true)
      (.withTransparent true)
      (.start)))

(defn filters-source
  "wraps a (HttpRequest, HttpResponse) => HttpResponse filter inside
  a HttpFiltersSource instance"
  [filter]
  (proxy [HttpFiltersSourceAdapter] []
    (filterRequest [request ctx]
      (proxy [HttpFiltersAdapter] [request]
        (serverToProxyResponse [response]
          (if-not (instance? HttpResponse response)
            response
            (filter request response)))))))

(defn start-server-mitm-on []
  "starts a HttpProxyServer instance with mitm filter enabled
  the instance is returned at the end"
  (let [source (new KeyStoreFileCertificateSource "PKCS12" (io/file c/key-store) "private-key" "123456")]
    (let [mitm (.build (.rootCertificateSource (ImpersonatingMitmManager/builder) source))]
      (-> (DefaultHttpProxyServer/bootstrap)
          (.withPort c/proxy-port)
          (.withAllowLocalOnly true)
          (.withManInTheMiddle mitm)
          (.withFiltersSource
            (filters-source
              (fn [request response]
                (if (should-allow request response @settings-atom)
                  (do (println "[ALLOW]" (find-url request)) response)
                  (do
                    (println "[DENY]" (find-url request))
                    (new DefaultFullHttpResponse
                         HttpVersion/HTTP_1_1
                         HttpResponseStatus/FORBIDDEN))))))
          (.start)))))

(defn start-server
  "starts or restarts the server with the given settings"
  ([] (start-server default-settings))
  ([settings]
   (locking server-lock
     (do
       (if @server-atom (.stop @server-atom))
       (reset! settings-atom settings)
       (reset! server-atom
               (if (:transparent settings)
                 (start-server-mitm-off)
                 (start-server-mitm-on)))))))
