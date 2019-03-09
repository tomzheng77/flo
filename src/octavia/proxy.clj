(ns octavia.proxy
  (:require [octavia.constants :as c]
            [octavia.utils :as u]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.set :as set])
  (:import (org.littleshoot.proxy.impl DefaultHttpProxyServer)
           (net.lightbody.bmp.mitm RootCertificateGenerator KeyStoreFileCertificateSource)
           (net.lightbody.bmp.mitm.manager ImpersonatingMitmManager)
           (org.littleshoot.proxy HttpFiltersSourceAdapter HttpFiltersAdapter)
           (io.netty.handler.codec.http HttpResponse DefaultFullHttpResponse HttpVersion HttpResponseStatus HttpHeaders HttpMessage HttpRequest)))

(def default-settings {})
(def example-settings {:not-contain-ctype #{"text/css"}
                       :start-with        #{"www.mvnrepository.com"}
                       :contain           "clojure"
                       :not-contain       #{"anime"}})

(defn no-restrictions [settings]
  (and (nil? (:not-contain-ctype settings))
       (nil? (:start-with settings))
       (nil? (:contain settings))
       (nil? (:not-contain settings))))

(defn file-exists [path]
  (let [file (io/file path)]
    (and
      (.exists file)
      (.isFile file)
      (.canRead file))))

(defn write-certificates []
  (if (some (complement file-exists) [c/certificate c/private-key c/key-store])
    (u/mkdirs c/certificate-root)
    (-> (.build (RootCertificateGenerator/builder))
        (.saveRootCertificateAsPemFile (io/file c/certificate))
        (.savePrivateKeyAsPemFile (io/file c/private-key) "123456")
        (.saveRootCertificateAndKey "PKCS12" (io/file c/key-store) "private-key" "123456"))))

(defn find-url
  "finds the destination URL of a HttpRequest"
  ([^HttpRequest request]
   (find-url
     (HttpHeaders/getHeader request "Host")
     (.getUri request)))
  ([host uri]
   (let [prefix #"^https?://"]
     (-> (if (re-find prefix uri) uri (str host uri))
         (str/replace-first prefix "")
         (u/take-before "?")))))

(defn find-content-type
  "finds the content type of a request or response"
  [^HttpMessage response]
  (HttpHeaders/getHeader response "Content-Type"))

(defn should-allow
  "tests whether the HTTP transaction should be allowed."
  ([request response settings]
   (should-allow
     (find-url request)
     (find-content-type response)
     settings nil))
  ([url content-type settings _]
   (true?
     (and
       (not-any? (fn [ctype] (str/includes? ctype content-type)) (u/set-of (:not-contain-ctype settings)))
       (not-any? (partial str/includes? url) (u/set-of (:not-contain settings)))
       (or
         (and (nil? (:start-with settings)) (nil? (:contain settings)))
         (some (partial str/starts-with? url) (u/set-of (:start-with settings)))
         (some (partial str/includes? url) (u/set-of (:contain settings))))))))

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
  (write-certificates)
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
               (if (no-restrictions settings)
                 (start-server-mitm-off)
                 (start-server-mitm-on)))))))

(defn inc-restrict
  "increases restrictions for the specified attribute on a settings object
  by adding or removing elements from the corresponding set."
  [settings attr rest]
  (let [combine (case attr
                  :not-contain-ctype set/union
                  :start-with set/intersection
                  :contain set/intersection
                  :not-contain set/union)]
    (assoc
      settings
      attr
      (combine
        (u/set-of (attr settings))
        (u/set-of rest)))))

(defn satisfy-both
  [settings-one settings-two]
  (-> settings-one
      (inc-restrict :not-contain-ctype (:not-contain-ctype settings-two))
      (inc-restrict :start-with (:start-with settings-two))
      (inc-restrict :contain (:contain settings-two))
      (inc-restrict :not-contain (:not-contain settings-two))))
