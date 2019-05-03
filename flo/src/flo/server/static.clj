(ns flo.server.static
  (:require [hiccup.page :refer [html5]]
            [garden.core :refer [css]]
            [flo.server.codec :refer [base64-encode]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]))

(defn login-html []
  (html5
    [:html {:lang "en"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:link {:rel "icon" :href "cljs-logo-icon-32.png"}]
      [:style
       (css [:html :body {:margin 0 :height "100%"}]
            [:body {:background-color "#95d5ee"}]
            [:#login-form {:width "300px"
                           :min-height "100px"
                           :margin-top "50px"
                           :margin-left "auto"
                           :margin-right "auto"
                           :background-color "red"}])]
      [:title "FloNote Login"]]
     [:body
      [:div#login-form]]]))

(defn editor-html [title init]
  (html5
    [:html {:lang "en"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:link {:rel "icon" :href "cljs-logo-icon-32.png"}]
      [:link {:href "css/style.css" :rel "stylesheet"}]
      [:style
       (css [:#app-inner
             {:margin "0" :display "flex" :flex-direction "column" :justify-content "center"}]
            [:html :body :#app :#app-inner {:margin 0 :height "100%"}]
            [:#editor :#editor-read-only
             {:flex-grow "1" :flex-shrink "1" :display "block" :border-bottom "none" :overflow-y "hidden"}])]
      [:title title]]
     [:body
      [:pre#anti-forgery-field (anti-forgery-field)]
      [:pre#init {:style "display: none"} (base64-encode (pr-str init))]
      [:div#app]
      [:script {:src "ace/ace.js" :type "text/javascript"}]
      [:script {:src "ace/ext-emmet.js" :type "text/javascript"}]
      [:script {:src "ace/ext-searchbox.js" :type "text/javascript"}]
      [:script {:src "ace/ext-options.js" :type "text/javascript"}]
      [:script {:src "ace/ext-whitespace.js" :type "text/javascript"}]
      [:script {:src "ace/ext-linking.js" :type "text/javascript"}]
      [:script {:src "ace/ext-language_tools.js" :type "text/javascript"}]
      [:script {:src "ace/ext-split.js" :type "text/javascript"}]
      [:script {:src "js/ace-ext-fs_previews.js" :type "text/javascript"}]
      [:script {:src "js/jquery-2.2.4.min.js" :type "text/javascript"}]
      [:script {:src "js/jquery.form.min.js" :type "text/javascript"}]
      [:script {:src "js/compiled/flo.js" :type "text/javascript"}]]]))
