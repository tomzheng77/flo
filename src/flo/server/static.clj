(ns flo.server.static
  (:require [hiccup.page :refer [html5]]
            [garden.core :refer [css]]
            [flo.server.codec :refer [base64-encode]]))

(defn login-html []
  (html5
    [:html {:lang "en"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:link {:rel "icon" :href "cljs-logo-icon-32.png"}]
      [:style
       (css [:html :body {:margin 0 :height "100%"}]
            [:body {:background-image "url('/dark-honeycomb.png')"
                    :display "flex"
                    :flex-direction "column"
                    :justify-content "center"
                    :align-items "center"}]
            ["@font-face"
             {:font-family "MadokaRunes"
              :src "url('/MadokaRunes-2.0.ttf')"}]
            [:#login-form
             {:padding       "10px"
              :border-radius "10px"}
             [:input
              {:width         "300px"
               :height        "50px"
               :line-height   "50px"
               :text-indent   "10px"
               :outline       "none"
               :border-radius "5px"
               :border "2px solid grey"
               :color "#555"
               :font-size "16px"}]])]
      [:title "FloNote Login"]]
     [:body
      [:div#login-form
       [:form {:method "POST" :action "/login"}
        [:input {:id "password" :name "password" :type "password"}]]]]]))

(defn editor-html [init]
  (html5
    [:html {:lang "en"}
     [:head
      [:meta {:charset "UTF-8"}]
      [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
      [:link {:rel "icon" :href "cljs-logo-icon-32.png"}]
      [:link {:href "css/style.css" :rel "stylesheet"}]
      [:link {:href "mxgraph/styles/grapheditor.css" :rel "stylesheet"}]
      [:style
       (css [:#app-inner
             {:margin "0" :display "flex" :flex-direction "column" :justify-content "center"}]
            [:html :body :#app :#app-inner {:margin 0 :height "100%"}]
            [:#editor :#editor-read-only
             {:flex-grow "1" :flex-shrink "1" :display "block" :border-bottom "none" :overflow-y "hidden"}])]
      [:title "FloNote"]]
     [:body
      [:pre#init {:style "display: none"} (base64-encode (pr-str init))]
      [:div#app]
      [:script {:src "mxgraph/js/sha1.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Init.js" :type "text/javascript"}]
      [:script {:src "mxgraph/deflate/pako.min.js" :type "text/javascript"}]
      [:script {:src "mxgraph/deflate/base64.js" :type "text/javascript"}]
      [:script {:src "mxgraph/jscolor/jscolor.js" :type "text/javascript"}]
      [:script {:src "mxgraph/sanitizer/sanitizer.min.js" :type "text/javascript"}]
      [:script {:src "mxgraph/src/js/mxClient.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/EditorUi.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Editor.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Sidebar.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Graph.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Format.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Shapes.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Actions.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Menus.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Toolbar.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/Dialogs.js" :type "text/javascript"}]
      [:script {:src "mxgraph/js/mxInterface.js" :type "text/javascript"}]

      [:script {:src "ace/ace.js" :type "text/javascript"}]
      [:script {:src "ace/ext-emmet.js" :type "text/javascript"}]
      [:script {:src "ace/ext-searchbox.js" :type "text/javascript"}]
      [:script {:src "ace/ext-options.js" :type "text/javascript"}]
      [:script {:src "ace/ext-whitespace.js" :type "text/javascript"}]
      [:script {:src "ace/ext-linking.js" :type "text/javascript"}]
      [:script {:src "ace/ext-language_tools.js" :type "text/javascript"}]
      [:script {:src "ace/ext-split.js" :type "text/javascript"}]
      [:script {:src "js/string.js" :type "text/javascript"}]
      [:script {:src "js/ace-ext-fs_previews.js" :type "text/javascript"}]
      [:script {:src "js/jquery-2.2.4.min.js" :type "text/javascript"}]
      [:script {:src "js/jquery.form.min.js" :type "text/javascript"}]
      [:script {:src "js/compiled/flo.js" :type "text/javascript"}]]]))
