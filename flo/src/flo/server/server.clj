(ns flo.server.server
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]
            [hiccup.page :refer [html5]]
            [garden.core :refer [css]]
            [clojure.core.match :refer [match]]
            [clojure.pprint :refer [pprint]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [org.httpkit.server :as ks]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.anti-forgery :as anti-forgery :refer [wrap-anti-forgery]]
            [clojure.core.async :as async :refer [chan <! <!! >! >!! put! chan go go-loop]]
            [clojure.set :as set]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [flo.server.store :refer [store]])
  (:import (java.util UUID)))

(defn on-chsk-receive [item]
  (match (:event item)
    [:flo/save [file-id contents]]
    (do (println file-id contents)
        (swap! store #(assoc % file-id contents)))
    :else nil))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn
              ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {:csrf-token-fn nil})]

  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids)
  (go-loop []
    (let [item (<! ch-chsk)]
      (on-chsk-receive item))
    (recur)))

(defroutes app-routes
  (route/resources "/" {:root "public"})
  (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (POST "/chsk" req (ring-ajax-post req))
  (GET "/style.css" []
    {:status  200
     :headers {"Content-Type" "text/css"}
     :body (css [:body {:margin "0"
                        :display "flex"
                        :flex-direction "column"
                        :justify-content "center"}]
                [:html :body {:height "100%"}]
                [:.ql-toolbar {:flex-shrink "0"}]
                [:.ql-container {:height "auto"}]
                [:#editor {:flex-grow "1"}])})
  (GET "/editor" request
    (let [file-id (get (:query-params request) "id" "default")
          content (get @store file-id {})]
      {:status  200
       :headers {"Content-Type" "text/html"}
       :session {:uid (.toString (UUID/randomUUID))}
       :body    (html5
                  [:html {:lang "en"}
                   [:head
                    [:meta {:charset "UTF-8"}]
                    [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                    [:link {:rel "icon" :href "cljs-logo-icon-32.png"}]
                    [:link {:href "css/quill.snow.css" :rel "stylesheet"}]
                    [:link {:href "style.css" :rel "stylesheet"}]
                    [:title "FloNote"]]
                   [:body
                    [:pre#init {:style "display: none"} (pr-str {:file-id file-id :content content})]
                    [:div#editor]
                    [:script {:src "js/compiled/flo.js" :type "text/javascript"}]]])}))
  (route/not-found "Not Found"))

;; NOTE: wrap reload isn't needed when the clj sources are watched by figwheel
;; but it's very good to know about
(def dev-app
  (-> app-routes
      (wrap-defaults site-defaults)
      (wrap-reload)
      (wrap-keyword-params)
      (wrap-params)))
