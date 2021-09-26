(ns flo.server.server
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [byte-streams]
            [digest]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]
            [hiccup.page :refer [html5]]
            [hiccup.util :refer [escape-html]]
            [garden.core :refer [css]]
            [clojure.core.match :refer [match]]
            [clojure.pprint :refer [pprint]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [taoensso.sente.packers.transit :as transit]
            [org.httpkit.server :as ks]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.anti-forgery :as anti-forgery :refer [wrap-anti-forgery]]
            [clojure.core.async :as async :refer [chan <! <!! >! >!! put! chan go go-loop]]
            [clojure.data :refer [diff]]
            [taoensso.timbre :as timbre :refer [trace debug info error]]
            [taoensso.timbre.appenders.core :as appenders]
            [flo.server.store :refer [get-note get-note-at set-note get-all-notes get-blob set-blob]]
            [flo.server.static :refer [editor-html login-html]]
            [flo.server.global :as global]
            [clojure.java.io :as io]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [clojure.string :as str])
  (:import (java.util UUID)
           (java.io FileInputStream)))

(timbre/set-config!
  {:level      :info
   :appenders  {:spit (appenders/spit-appender {:fname "flo.log"})}})

(defn chsk-send! [& args])
(defn send-note-contents [uid name timestamp & [content]]
  (let [note (assoc (get-note name) :time timestamp)]
    (if content
      (chsk-send! uid [:flo/refresh (assoc note :content content :time-updated (System/currentTimeMillis))])
      (chsk-send! uid [:flo/refresh note]))))

; map of client-id => timestamp
(def seek-location (atom {}))
(def seek-signal (chan))
(def seek-signal-on (atom false))

(defonce run-iteration-id (atom (UUID/randomUUID)))
(reset! run-iteration-id (UUID/randomUUID))

(def connected-uids (atom #{}))
(defn on-chsk-receive [{:keys [event uid]}]
  (match event
    [:flo/seek [name timestamp]]
    (do (swap! seek-location #(assoc % uid [name timestamp]))
        (when-not @seek-signal-on
          (reset! seek-signal-on true)
          (>!! seek-signal true)))
    [:flo/save [name timestamp content]]
    (do (debug "saving" name)
        (set-note name content)
        (chsk-send! uid [:flo/saved [name (System/currentTimeMillis)]])
        (doseq [other-uid (:any @connected-uids)]
          (when (not= uid other-uid)
            (send-note-contents other-uid name timestamp content))))
    :else nil))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn
              ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter)
        {:csrf-token-fn nil :packer (transit/get-transit-packer)})]

  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids)
  (go-loop []
    (let [item (<! ch-chsk)]
      (on-chsk-receive item))
    (recur)))

(let [init-id @run-iteration-id]
  (go (while (= init-id @run-iteration-id)
    (let [_ (<! seek-signal)]
      (reset! seek-signal-on false)
      (locking seek-location
        (when (not-empty @seek-location)
          (doseq [[uid [name timestamp]] @seek-location]
            (let [note (get-note-at name timestamp)]
              (when note (chsk-send! uid [:flo/history note]))))
          (reset! seek-location {})))))))

; stores a file of the format
;{:filename "1.in",
;   :content-type "application/octet-stream",
;   :tempfile
;   #object[java.io.File 0x29c0647a "/tmp/ring-multipart-8829745542880348189.tmp"],
;   :size 57}}
(defn store-file [{:keys [filename content-type tempfile size]} uuid]
  (let [out-file (io/file @global/upload-dir (.toString uuid))
        edn-file (io/file @global/upload-dir (str (.toString uuid) ".edn"))]
    (io/copy tempfile out-file)
    (spit edn-file (pr-str {:name filename :content-type content-type :size size}))))

(def redirect-login {:status 302 :headers {"Location" "/login"} :body ""})

(defroutes app-routes
  (route/resources "/" {:root "public"})
  (GET "/chsk" request
    (if-not (:login (:session request))
      {:status 302 :headers {"Location" "/login"} :body ""}
      (ring-ajax-get-or-ws-handshake request)))
  (POST "/chsk" request
    (if-not (:login (:session request))
      {:status 302 :headers {"Location" "/login"} :body ""}
      (ring-ajax-post request)))
  (GET "/file" request
    (if-not (:login (:session request))
      {:status 302 :headers {"Location" "/login"} :body ""}
      (let [id (get (:query-params request) "id")
            file (io/file @global/upload-dir id)
            edn (try (read-string (slurp (io/file @global/upload-dir (str id ".edn")))) (catch Exception _ {}))
            content-type (or (:content-type edn) "text/plain")]
        (if (and (.exists file) (.isFile file) (.canRead file))
          {:status 200 :headers {"Content-Type" content-type} :body (new FileInputStream file)}
          {:status 404 :headers {"Content-Type" "text/plain"} :body (str (pr-str id) " Not Found")}))))
  (POST "/file-upload" request
    (if-not (:login (:session request))
      redirect-login
      (let [response
        (let [files (:files (:params request))]
          (for [file (if (vector? files) files [files])]
            (let [uuid (UUID/randomUUID)]
              (store-file file uuid)
              {:name (:filename file) :id (.toString uuid)})))]
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body (pr-str (vec response))})))
  (GET "/blob" request
    (if-not (:login (:session request))
      redirect-login
      (let [hash (get (:query-params request) "hash")
            buffer (:buffer (get-blob hash))]
        (if buffer
          {:status 200 :headers {"Content-Type" "application/octet-stream"} :body buffer}
          {:status 404 :headers {"Content-Type" "text/plain"} :body (str (pr-str hash) " Not Found")}))))
  (POST "/blob-upload" request
    (if-not (:login (:session request))
      redirect-login
      (let [response
        (let [files (:files (:params request))]
          (for [file (if (vector? files) files [files])]
            (let [buffer (byte-streams/to-byte-array file)
                  hash (digest/sha1 buffer)]
              (if-not (:buffer (get-blob hash))
                (set-blob hash buffer))
              {:hash hash})))]
        {:status 200
         :headers {"Content-Type" "text/plain"}
         :body (pr-str (vec response))})))
  (POST "/login" request
    (if (:login (:session request))
      {:status 302 :headers {"Location" "/editor"} :session (:session request) :body ""}
      (let [password (:password (:params request)) session (:session request)]
        (if (or (empty? @global/password) (= @global/password password))
          {:status 302 :headers {"Location" "/editor"} :body "" :session (assoc session :login true)}
          {:status 302 :headers {"Location" "/login"} :body "" :session session}))))
  (GET "/login" request
    (if (:login (:session request))
      {:status  302 :headers {"Location" "/editor"} :body ""}
      (if (empty? @global/password)
        {:status 302
         :headers {"Location" "/editor"}
         :session (assoc (:session request) :login true)
         :body ""}
        {:status 200
         :headers {"Content-Type" "text/html"}
         :session (:session request)
         :body (login-html)})))
  (GET "/" [] redirect-login)
  (GET "/history" request
    (if-not (:login (:session request))
      redirect-login
      (let [time (get (:query-params request) "t" "2019-05-06T12:00:00")
            notes (get-all-notes time)
            session (assoc (:session request) :uid (.toString (UUID/randomUUID)))
            field (anti-forgery-field)]
        {:status  200
         :headers {"Content-Type" "text/html"}
         :session session
         :body    (editor-html
                    {:notes notes
                     :anti-forgery-field field
                     :read-only true})})))
  (GET "/editor" request
    (if-not (:login (:session request))
      redirect-login
      (let [notes (get-all-notes)
            session (assoc (:session request) :uid (.toString (UUID/randomUUID)))
            field (anti-forgery-field)]
        {:status  200
         :headers {"Content-Type" "text/html"}
         :session session
         :body    (editor-html
                    {:notes notes
                     :anti-forgery-field field})})))
  (route/not-found "Not Found"))

;; NOTE: wrap reload isn't needed when the clj sources are watched by figwheel
;; but it's very good to know about
(def dev-app
  (-> app-routes
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))
      (wrap-reload)
      (wrap-keyword-params)
      (wrap-params)))

; converts a parameter array into a map of named parameters
(defn named-params [params]
  (loop [remain (seq params) acc {}]
    (if (< (count remain) 2)
      acc
      (let [head (first remain) next (nth remain 1)]
        (if (not (str/starts-with? head "--"))
          (recur (rest remain) acc)
          (recur (rest (rest remain)) (assoc acc (subs head 2) next)))))))

; password
; port
; database name
(defn -main [& params]
  (let [named (named-params params)
        password (or (get named "password") (str @global/password))
        port (read-string (or (get named "port") (str @global/port)))
        db (or (get named "db") (str @global/db-name))
        upload-dir (or (get named "upload-dir") (str @global/upload-dir))]
    (reset! global/password password)
    (reset! global/port port)
    (reset! global/db-name db)
    (reset! global/upload-dir upload-dir)
    (.mkdirs (io/file @global/upload-dir))
    (ks/run-server dev-app {:port port})))
