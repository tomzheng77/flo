(ns flo.client.editor.editor-graph
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.constants :as c]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [clojure.string :as str]
    [cljsjs.moment]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]
    [reagent.dom :as rd]
    [clojure.set :as set]))

(defn view-render [active?]
  (fn []
    [:div {:style {:flex-grow 1 :display (if @active? "flex" "none") :flex-direction "column"}}
      [:div.container-graph-editor.geEditor {:style {:height "100%" :position "relative"}}]]))

(defn initialize [graph-editor read-only? event-handler comp]
  (let [element (aget (.-childNodes (rd/dom-node comp)) 0)]
    (reset! graph-editor (js/mxInterfaceInit element))))

; ========== [PUBLIC METHODS] ==========

(defn new-instance
  ([] (new-instance {}))
  ([{:keys [read-only? event-handler init-active?]}]
   (let [graph-editor (r/atom nil) active? (r/atom (if (nil? init-active?) true init-active?))]
     {:instance-type :client-graph-editor
      :view (fn []
        (r/create-class {
          :reagent-render #(view-render active?)
          :component-did-mount
          #(initialize graph-editor (if (nil? read-only?) false read-only?) event-handler %)}))
      :graph-editor graph-editor
      :active? active?
      :event-handler (or event-handler (fn []))})))

(defn get-content [this] (js/mxInterfaceGetContent @(:graph-editor this)))
(defn set-content [this content] (js/mxInterfaceSetContent @(:graph-editor this) content))
(defn open-note
  ([this note] (open-note this note nil))
  ([this {:keys [content]} open-opts] (set-content this content)))

; OPTIONAL, for better user experience
(defn focus [this])
(defn on-press-key [this event])
(defn on-release-key [this event])
(defn on-window-blur [this event])

; DEPRECATED, since it is preferred that images are stored as blobs
(defn insert-image [this image-id])
