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
            [flo.server.store :refer [get-note set-note get-note-created get-note-updated]]
            [org.httpkit.server :as ks]
            [taoensso.timbre :as timbre :refer [trace debug info error]])
  (:import (java.util Base64)))

(defn base64-encode [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))

(defn indent-styles [initial step]
  [[:li {:padding-left (str initial "em") :list-style "none"}]
   (for [i (range 1 9)]
     [(str ".ql-indent-" i) {:padding-left (str (+ initial (* step i)) "em")}])])

(def style-css
  (css [:#app-inner {:margin          "0"
                     :display         "flex"
                     :flex-direction  "column"
                     :justify-content "center"}]
       [:html :body :#app :#app-inner {:margin 0 :height "100%"}]
       [:.ql-toolbar {:flex-shrink "0"}]
       [:.ql-container {:height "auto"}]
       [:#editor :#editor-read-only
        {:flex-grow "1" :flex-shrink "1" :display "block" :border-bottom "none" :overflow-y "hidden"}
        ["::selection" {:background-color "#3DA1D2" :color "#FFF"}]
        [:.ql-editor [:.ql-syntax {:font-size "11px" :opacity 1}]]
        [:.ql-editor
         [:ol :ul {:padding-left "0"}
          [:li:before {:content "'-'"}]
          (indent-styles 1 2)]]]))

(defn index-html [init-data]
  (html5
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
      [:pre#init {:style "display: none"} (base64-encode (pr-str init-data))]
      [:div#app]
      [:script {:src "js/highlight.pack.js" :type "text/javascript"}]
      [:script {:src "js/compiled/flo.js" :type "text/javascript"}]]]))
