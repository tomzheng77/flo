(ns flo.server
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]
            [hiccup.core :refer [html]]
            [clojure.core.match :refer [match]]
            [clojure.pprint :refer [pprint]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [org.httpkit.server :as ks]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.anti-forgery :as anti-forgery :refer [wrap-anti-forgery]]
            [clojure.core.async :as async :refer [chan <! <!! >! >!! put! chan go go-loop]]
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

(defn read-contents [] (read-string (slurp "contents.edn")))
(defn write-contents
  [contents]
  (spit "contents.edn" (pr-str contents)))

(def contents (atom (read-contents)))

; this will start a thread which continuously writes
; the latest version of contents
(let [contents-last-write (atom [])
      signals (chan)
      signal-count (atom 0)]
  ; whenever the value of contents changes, add a new signal
  ; the signal can be any value
  (add-watch contents :rewrite
    (fn [_ _ _ _]
      (when (> 512 @signal-count)
        (swap! signal-count inc)
        (>!! signals 0))))
  (go-loop []
    (let [_ (<! signals)]
      (swap! signal-count dec)
      (let [now-contents @contents]
        (when (not= now-contents @contents-last-write)
          (write-contents now-contents)
          (reset! contents-last-write now-contents))))
    (recur)))

(add-watch
  connected-uids :send-contents
  (fn [key ref old new]
    (let [added (set/difference (:any new) (:any old))]
      (doseq [uid added]
        (when (not= ::sente/nil-uid uid)
          (println "sending to" uid)
          (chsk-send! uid [:flo/load @contents]))))))

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
  (GET "/" request
    (println request)
    {:status  200
     :headers {"Content-Type" "text/html"}
     :session {:uid (.toString (UUID/randomUUID))}
     :body    (html
                "<!DOCTYPE html>\n"
                [:html {:lang "en"}
                 [:head
                  [:meta {:charset "UTF-8"}]
                  [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
                  [:link {:rel "icon" :href "https://clojurescript.org/images/cljs-logo-icon-32.png"}]
                  [:link {:href "css/quill.snow.css" :rel "stylesheet"}]
                  [:title "FloNote"]]
                 [:body
                  [:div#editor {:style "height: 500px"}]
                  [:script {:src "js/compiled/flo.js" :type "text/javascript"}]]])})
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
