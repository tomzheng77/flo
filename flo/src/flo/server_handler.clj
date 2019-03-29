(ns flo.server-handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]

            [clojure.core.match :refer [match]]
            [clojure.pprint :refer [pprint]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [org.httpkit.server :as ks]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.anti-forgery :as anti-forgery :refer [wrap-anti-forgery]]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
            [clojure.set :as set])
  (:import (java.util UUID)))

(let [{:keys [ch-recv
              send-fn
              connected-uids
              ajax-post-fn
              ajax-get-or-ws-handshake-fn]}

      (sente/make-channel-socket! (get-sch-adapter) {:csrf-token-fn nil})]

  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(defn contents (atom {}))

(add-watch
  connected-uids "watch"
  (fn [key ref old new]
    (let [added (set/difference (:any new) (:any old))]
      (doseq [uid added]
        (println "sending to" uid)
        (chsk-send! uid [:flo/load @contents])))))

(defn on-chsk-receive [item]
  (match (:event item)
    [:flo/save c] (reset! contents c)
    :else nil))

(defonce start-loop
  (go-loop []
    (let [item (<! ch-chsk)]
      (on-chsk-receive item))
    (recur)))

(defroutes
  app-routes
  ;; NOTE: this will deliver all of your assets from the public directory
  ;; of resources i.e. resources/public
  (route/resources "/" {:root "public"})
  ;; NOTE: this will deliver your index.html
  (GET "/" [] (-> (response/resource-response "index.html" {:root "public"})
                  (response/content-type "text/html")))
  (GET "/hello" [] "Hello World!")
  (GET "/login" req
    (let [csrf-token (force anti-forgery/*anti-forgery-token*)]
      {:status  200
       :headers {"Content-Type" "text/plain"}
       :body    (pr-str {:csrf-token csrf-token})
       :session {:uid (.toString (UUID/randomUUID))}}))
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (route/not-found "Not Found"))

;; NOTE: wrap reload isn't needed when the clj sources are watched by figwheel
;; but it's very good to know about
(def dev-app
  (-> app-routes
      (wrap-defaults site-defaults)
      (wrap-reload)
      (wrap-keyword-params)
      (wrap-params)))
