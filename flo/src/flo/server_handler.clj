(ns flo.server-handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.util.response :as response]

            [clojure.pprint :refer [pprint]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [org.httpkit.server :as ks]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.anti-forgery :as anti-forgery :refer [wrap-anti-forgery]]
            [clojure.core.async :as async :refer (<! <!! >! >!! put! chan go go-loop)]
            [clojure.set :as set]))

(let [{:keys [ch-recv
              send-fn
              connected-uids
              ajax-post-fn
              ajax-get-or-ws-handshake-fn]}

      (sente/make-channel-socket! (get-sch-adapter))]

  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(add-watch
  connected-uids "watch"
  (fn [key ref old new]
    (let [added (set/difference (:any new) (:any old))]
      (doseq [uid added]
        (println "sending to" uid)
        (chsk-send! uid [:flo/load "this is a test message"])))))

(defonce
  handler
  (go-loop []
    (let [item (<! ch-chsk)]
      (let [[type msg] (:event item)]
        (println type msg)
        (when (= :flo/save type)
          (println msg))))
    (recur)))

;; If you are new to using Clojure as an HTTP server please take your
;; time and study ring and compojure. They are both very popular and
;; accessible Clojure libraries.

;; --> https://github.com/ring-clojure/ring
;; --> https://github.com/weavejester/compojure

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
       :session {:uid 0}}))
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
      (wrap-params)
      (wrap-anti-forgery)))
