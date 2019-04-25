(ns flo.server.static
  (:require [hiccup.page :refer [html5]]
            [garden.core :refer [css]])
  (:import (java.util Base64)))

(defn base64-encode [to-encode]
  (.encodeToString (Base64/getEncoder) (.getBytes to-encode)))

(def style-css
  (css [:#app-inner
        {:margin "0" :display "flex" :flex-direction "column" :justify-content "center"}]
       [:html :body :#app :#app-inner {:margin 0 :height "100%"}]
       [:#editor :#editor-read-only
        {:flex-grow "1" :flex-shrink "1" :display "block" :border-bottom "none" :overflow-y "hidden"}]))

(defn editor-html [init]
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
