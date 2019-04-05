(ns flo.server.server
  (:gen-class)
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
            [taoensso.sente.packers.transit :as transit]
            [org.httpkit.server :as ks]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.anti-forgery :as anti-forgery :refer [wrap-anti-forgery]]
            [clojure.core.async :as async :refer [chan <! <!! >! >!! put! chan go go-loop]]
            [clojure.set :as set]
            [clojure.data :refer [diff]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [flo.server.store :refer [store]]
            [org.httpkit.server :as ks]
            [taoensso.timbre :as timbre :refer [trace debug info error]]
            [taoensso.timbre.appenders.core :as appenders])
  (:import (java.util UUID)
           (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)))

(timbre/merge-config!
  {:hostname_  "local"
   :level      :debug
   :appenders  {:spit (appenders/spit-appender {:fname "flo.log"})}})

(defn on-chsk-receive [item]
  (match (:event item)
    [:flo/save [file-id contents]]
    (do (debug "saving" file-id)
        (swap! store #(assoc % file-id contents)))
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

(defn indent-styles [initial step]
  [[:li {:padding-left (str initial "em") :list-style "none"}]
   (for [i (range 1 9)]
     [(str ".ql-indent-" i) {:padding-left (str (+ initial (* step i)) "em")}])])

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
                [:#editor {:flex-grow "1" :flex-shrink "1" :display "block" :border-bottom "none" :overflow-y "hidden"}
                 ["::selection" {:background-color "#3DA1D2" :color "#FFF"}]
                 [:.ql-editor
                  [:ol :ul {:padding-left "0"}
                   [:li:before {:content "'-'"}]
                   (indent-styles 1 2)]]]

                ; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
                [:#status {:height "40px"
                           :background-color "#3DA1D2"
                           :line-height "40px"
                           :color "#FFF"
                           :font-family "Monospace"
                           :text-indent "10px"
                           :flex-grow "0"
                           :flex-shrink "0"}])})
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
                    [:link {:href "css/highlight/monokai-sublime.css" :rel "stylesheet"}]
                    [:link {:href "style.css" :rel "stylesheet"}]
                    [:title "FloNote"]]
                   [:body
                    [:pre#init {:style "display: none"} (pr-str {:file-id file-id :content content})]
                    [:div#editor]
                    [:div#status]
                    [:script {:src "js/highlight.pack.js" :type "text/javascript"}]
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

(defn -main [& args]
  (ks/run-server dev-app {:port 3450}))
