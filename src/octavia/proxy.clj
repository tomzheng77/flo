(ns octavia.proxy
  (:require [octavia.constants :as c]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (org.littleshoot.proxy.impl DefaultHttpProxyServer)
           (net.lightbody.bmp.mitm RootCertificateGenerator KeyStoreFileCertificateSource)
           (net.lightbody.bmp.mitm.manager ImpersonatingMitmManager)
           (org.littleshoot.proxy HttpFiltersSourceAdapter HttpFiltersAdapter)
           (io.netty.handler.codec.http HttpResponse DefaultFullHttpResponse HttpVersion HttpResponseStatus HttpHeaders HttpMessage HttpRequest)
           (java.util.regex Pattern)))

(def default-settings {})
(def example-settings {:must-not-contain-ctype #{"text/css"}
                       :must-start-with        #{"www.mvnrepository.com"}
                       :must-contain           "clojure"
                       :must-not-contain       #{"anime"}})

(defn no-restrictions [settings]
  (and (nil? (:must-not-contain-ctype settings))
       (nil? (:must-start-with settings))
       (nil? (:must-contain settings))
       (nil? (:must-not-contain settings))))

(defn mkdirs [file-path] (.mkdirs (io/file file-path)))

(defn file-exists [path]
  (let [file (io/file path)]
    (and
      (.exists file)
      (.isFile file)
      (.canRead file))))

(defn write-certificates []
  (if (some (complement file-exists) [c/certificate c/private-key c/key-store])
    (mkdirs c/certificate-root)
    (-> (.build (RootCertificateGenerator/builder))
        (.saveRootCertificateAsPemFile (io/file c/certificate))
        (.savePrivateKeyAsPemFile (io/file c/private-key) "123456")
        (.saveRootCertificateAndKey "PKCS12" (io/file c/key-store) "private-key" "123456"))))

(defn take-before
  [str char]
  (let [index (str/index-of str char)]
    (if (nil? index) str (subs str 0 index))))

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
         (take-before "?")))))

(defn find-content-type
  "finds the content type of a request or response"
  [^HttpMessage response]
  (HttpHeaders/getHeader response "Content-Type"))

(defn set-of [item-or-coll]
  (if (coll? item-or-coll)
    (set item-or-coll)
    #{item-or-coll}))

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
       (not-any? (fn [ctype] (str/includes? ctype content-type)) (set-of (:must-not-contain-ctype settings)))
       (not-any? (partial str/includes? url) (set-of (:must-not-contain settings)))
       (or
         (and (nil? (:must-start-with settings)) (nil? (:must-contain settings)))
         (some (partial str/starts-with? url) (set-of (:must-start-with settings)))
         (some (partial str/includes? url) (set-of (:must-contain settings))))))))

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