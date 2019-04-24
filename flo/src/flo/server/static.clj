(ns flo.server.static
  (:require [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [hiccup.page :refer [html5]]
            [hiccup.util :refer [escape-html]]
            [garden.core :refer [css]]
            [clojure.core.match :refer [match]]
            [clojure.pprint :refer [pprint]]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.data :refer [diff]]
            [flo.server.store :refer [get-note-content set-note get-note-created get-note-updated]]
            [org.httpkit.server :as ks]
            [taoensso.timbre :as timbre :refer [trace debug info error]])
  (:import (java.util Base64)))

(defn base64-encode [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))

(def style-css
  (css [:#app-inner
        {:margin "0" :display "flex" :flex-direction "column" :justify-content "center"}]
       [:html :body :#app :#app-inner {:margin 0 :height "100%"}]
       [:#editor :#editor-read-only
        {:flex-grow "1" :flex-shrink "1" :display "block" :border-bottom "none" :overflow-y "hidden"}]))

(defn index-html [init]
  (html5
    [:html {:lang "en"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:link {:rel "icon" :href "cljs-logo-icon-32.png"}]
      [:link {:href "css/highlight/monokai-sublime.css" :rel "stylesheet"}]
      [:style style-css]
      [:title "FloNote"]]
     [:body
      [:pre#init {:style "display: none"} (base64-encode (pr-str init))]
      [:div#app]
      [:script {:src "js/highlight.pack.js" :type "text/javascript"}]
      [:script {:src "js/compiled/flo.js" :type "text/javascript"}]]]))
