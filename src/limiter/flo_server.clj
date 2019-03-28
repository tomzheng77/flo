(ns limiter.flo-server
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [org.httpkit.server :as ks]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.params :as params]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      ; TODO: enable CSRF token
      (sente/make-channel-socket! (get-sch-adapter) {:csrf-token-fn nil})]

  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(defroutes
  my-app-routes
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req)))

(def my-app
  (-> my-app-routes
      keyword-params/wrap-keyword-params
      params/wrap-params))

(defn run []
  (let [server (ks/run-server my-app {:port 9050})]
    (println server)
    (go-loop []
      (let [item (<! ch-chsk)]
        (println "received item")
        (println item))
      (recur))))
