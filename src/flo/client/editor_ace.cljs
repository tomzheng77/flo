(ns flo.client.editor-ace
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.ace.ace :as ace]
    [flo.client.ace.ace-clickables]
    [flo.client.ace.ace-colors]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
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

(defn next-tag [editor direction]
  (if (= :up direction)
    (ace/navigate editor c/any-navigation-inner {:backwards true})
    (ace/navigate editor c/any-navigation-inner)))

(defn command-toggle-nav [editor event-handler]
  {:name "toggle-navigation"
   :exec #(event-handler [:toggle-navigation])
   :bindKey {:mac "cmd-p" :win "ctrl-p"}
   :readOnly true})

(defn command-tab [editor]
  {:name "tab-command"
   :exec #(ace/indent-selection editor)
   :bindKey {:mac "tab" :win "tab"}
   :readOnly false})

(defn command-ctrl-up [editor]
  {:name "up-tag"
   :exec #(next-tag editor :up)
   :bindKey {:mac "cmd-up" :win "ctrl-up"}
   :readOnly true})

(defn command-ctrl-down [editor]
  {:name "down-tag"
   :exec #(next-tag editor :down)
   :bindKey {:mac "cmd-down" :win "ctrl-down"}
   :readOnly true})

(defn command-insert-time [editor]
  {:name "insert-time"
   :exec #(ace/insert-at-cursor editor (.format (js/moment) "YYYY-MM-DD HH:mm:ss"))
   :bindKey {:mac "cmd-q" :win "ctrl-q"}
   :readOnly false})

(defn view-render [active?]
  (fn []
    [:div {:style {:flex-grow 1 :display (if @active? "flex" "none") :flex-direction "column"}}
      [:div.container-ace-editor {:style {:height "100%"}}]]))

(defn initialize [ace-editor read-only? event-handler comp]
  (let [element (aget (.-childNodes (rd/dom-node comp)) 0)]
    (reset! ace-editor (ace/new-instance element))
    (ace/set-read-only @ace-editor read-only?)

    (.on @ace-editor "change" #(if-not (.-autoChange @ace-editor) (event-handler [:change %])))
    (.on @ace-editor "changeSelection"
      #(if-not (.-autoChangeSelection @ace-editor)
         (event-handler [:change-selection (ace/get-selection @ace-editor)])))

    (.addCommand (.-commands @ace-editor) (clj->js (command-toggle-nav @ace-editor event-handler)))
    (.addCommand (.-commands @ace-editor) (clj->js (command-tab @ace-editor)))
    (.addCommand (.-commands @ace-editor) (clj->js (command-ctrl-up @ace-editor)))
    (.addCommand (.-commands @ace-editor) (clj->js (command-ctrl-down @ace-editor)))
    (.addCommand (.-commands @ace-editor) (clj->js (command-insert-time @ace-editor)))))

(defn new-instance
  ([] (new-instance {}))
  ([{:keys [read-only? event-handler init-active?]}]
   (let [ace-editor (r/atom nil) active? (r/atom (if (nil? init-active?) true init-active?))]
     {:instance-type :client-ace-editor
      :view (fn []
        (r/create-class {
          :reagent-render #(view-render active?)
          :component-did-mount
          #(initialize ace-editor (if (nil? read-only?) false read-only?) event-handler %)}))
      :ace-editor ace-editor
      :active? active?
      :event-handler (or event-handler (fn []))})))

(defn open-note
  ([this note] (open-note note nil))
  ([{:keys [ace-editor]} {:keys [content selection]} {:keys [search]}]
   (set! (.-autoChangeSelection @ace-editor) true)
   (ace/set-text @ace-editor (or content ""))
   (js/setTimeout
     #(do (ace/set-selection @ace-editor selection)
          (ace/navigate @ace-editor search)
          (.focus @ace-editor)
          (set! (.-autoChangeSelection @ace-editor) false)) 0)))

(defn copy-state-from [this another]
  (let [{:keys [ace-editor]} this another-ace-editor (another :ace-editor)]
    (set! (.-autoChangeSelection @ace-editor) true)
    (ace/set-text @ace-editor (ace/get-text @another-ace-editor))
    (js/setTimeout
      #(do (ace/set-selection @ace-editor (ace/get-selection @another-ace-editor))
           (.focus @ace-editor)
           (set! (.-autoChangeSelection @ace-editor) false)) 0)))

(defn goto-search [this search backwards]
  (let [{:keys [active? ace-editor]} this]
    (ace/navigate @ace-editor search {:backwards backwards})))

(defn insert-image [this image-id]
  (let [{:keys [ace-editor]} this]
    (ace/insert-at-cursor @ace-editor (str "[*" image-id "]\n"))))

(defn set-editable [this editable?]
  (let [{:keys [ace-editor]} this]
    (ace/set-read-only @ace-editor (not editable?))))

(defn focus [this]
  (let [{:keys [active? ace-editor]} this]
    (.focus @ace-editor)))

(defn get-content [this]
  (let [{:keys [active? ace-editor]} this]
    (ace/get-text @ace-editor)))

(defn set-content [this content]
  (let [{:keys [ace-editor]} this]
    (ace/set-text @ace-editor content)))

(defn on-press-key
  [this {:keys [code key ctrl-key shift-key original]}]
  (let [{:keys [active? ace-editor]} this]
    (when @active?
      (when (= "Control" key)
        (ace/show-clickables @ace-editor))
      (when (and ctrl-key (= "q" key))
        (.preventDefault original)
        (ace/insert-at-cursor @ace-editor (.format (js/moment) "YYYY-MM-DD HH:mm:ss"))))))

(defn on-release-key
  [this {:keys [code key ctrl-key shift-key original]}]
  (let [{:keys [active? ace-editor]} this]
    (when @active?
      (when (= "Control" key)
        (ace/hide-clickables @ace-editor)))))

(defn on-window-blur [this]
  (let [{:keys [active? ace-editor]} this]
    (when @active?
      (ace/hide-clickables @ace-editor))))
