(ns flo.server.server
  (:gen-class)
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
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
            [flo.server.store :refer [get-note-content get-note-at set-note get-note-created get-note-updated get-notes-summary]]
            [flo.server.static :refer [editor-html login-html]])
  (:import (java.util UUID Date)))

(timbre/merge-config!
  {:level      :debug
   :appenders  {:spit (appenders/spit-appender {:fname "flo.log"})}})

; map of client-id => timestamp
(def seek-location (atom {}))
(defonce run-iteration-id (atom (UUID/randomUUID)))
(reset! run-iteration-id (UUID/randomUUID))

(defn on-chsk-receive [item]
  (match (:event item)
    [:flo/seek [file-id timestamp]]
    (do (swap! seek-location #(assoc % (:uid item) [file-id timestamp])))
    [:flo/save [file-id content]]
    (do (debug "saving" file-id)
        (set-note file-id content))
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
        (locking seek-location
          (when (not-empty @seek-location)
            (doseq [[uid [file-id timestamp]] @seek-location]
              (let [note (get-note-at file-id timestamp)]
                (when note
                  (chsk-send! uid [:flo/history [file-id
                                                 (.getTime (:updated note))
                                                 (:content note)]]))))
            (reset! seek-location {})))
        (Thread/sleep 50))))

(defroutes app-routes
  (route/resources "/" {:root "public"})
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (GET "/login" []
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (login-html)})
  (GET "/editor" request
    (let [file-id (get (:query-params request) "id" "default")
          content (get-note-content file-id)
          time-created (.getTime (or (get-note-created file-id) (new Date)))
          time-updated (.getTime (or (get-note-updated file-id) (new Date)))
          notes-summary (get-notes-summary)
          session {:uid (.toString (UUID/randomUUID))}]
      {:status  200
       :headers {"Content-Type" "text/html"}
       :session session
       :body    (editor-html file-id
                  {:file-id       file-id
                   :content       content
                   :time-created  time-created
                   :time-updated  time-updated
                   :notes-summary notes-summary
                   })}))
           (route/not-found "Not Found"))

;; NOTE: wrap reload isn't needed when the clj sources are watched by figwheel
;; but it's very good to know about
(def dev-app
  (-> app-routes
      (wrap-defaults site-defaults)
      (wrap-reload)
      (wrap-keyword-params)
      (wrap-params)))

(defn -main [& args]
  (ks/run-server dev-app {:port 3451}))
