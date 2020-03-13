(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.editor :as editor]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
    [flo.client.store :refer [add-watches-db add-watch-db db active-history]]
    [flo.client.network]
    [flo.client.view :refer [navigation search-bar history-bar]]
    [flo.client.constants :as c]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.data.avl :as avl]
    [cljsjs.moment]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]
    [reagent.dom :as rd]
    [re-frame.core :as rf]
    [clojure.set :as set]))

(enable-console-print!)
(defonce init
         (->> "init"
              (.getElementById js/document)
              (.-innerHTML)
              (b64/decodeStringToByteArray)
              (js/stringFromUTF8Array)
              (read-string)))

(def anti-forgery-field (:anti-forgery-field init))
(defn file-uploaded [response]
  ; add [*b82b6c5e-6d44-11e9-a923-1681be663d3e]
  ; editor.session.insert(editor.getCursorPosition(), text)
  (doseq [{:keys [id]} response]
    (editor/insert-image id)))

(defn upload-image []
  (.ajaxSubmit (js/$ "#file-form")
    (clj->js {:success #(file-uploaded (read-string %))})))

(defn file-form-render []
  [:form {:id "file-form" :method "POST" :action "/file-upload" :encType "multipart/form-data"}
   [:div {:style {:display "none"} :dangerouslySetInnerHTML {:__html anti-forgery-field}}]
   [:input {:id "file-input" :type "file" :style {:display "none"} :multiple true
            :on-change #(upload-image) :name "files"}]])

(defn file-form []
  (r/create-class
    {:reagent-render file-form-render
     :component-did-mount (fn [_])}))

(when-not js/document.initialized
  (set! (.-initialized js/document) true)
  (let [href js/window.location.href]
    (rf/dispatch-sync [:initialize (current-time-millis) init href])))

(rf/reg-fx :set-hash (fn [hash] (set! (.. js/window -location -hash) hash)))
(rf/reg-fx :set-title (fn [title] (set! (.-title js/document) title)))

; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
(defn app []
  [:div#app-inner
   [file-form]
   (if @(rf/subscribe [:navigation]) ^{:key "nav"} [navigation])
   [editor/view]
   (if @(rf/subscribe [:search]) [search-bar])
   [history-bar]])

(rd/render [app] (js/document.getElementById "app"))

(defn save-editor-content []
  (let [{:keys [name content]} (editor/get-name-and-content)]
    (when name
      (rf/dispatch [:editor-save name content]))))

(rf/reg-fx :focus-editor
  (fn [_]
    (editor/focus)))

(rf/reg-fx :accept-external-change
  (fn [note]
    (editor/accept-external-change note)))

; copies all the contents of ace-editor-ro and displays them to ace-editor
(rf/reg-fx :open-note-after-preview
  (fn [note]
    (save-editor-content)
    (editor/open-note-after-preview note)))

(rf/reg-fx :open-note
  (fn [[note search]]
    (save-editor-content)
    (editor/open-note note {:search search})))

(rf/reg-fx :preview-note
  (fn [[note search]]
    (editor/preview-note note {:search search})))

(add-watches-db :open-history [[:history-cursor] active-history [:history-direction]]
  (fn [_ _ _ [timestamp history direction]]
    (when-not timestamp
      (editor/close-history))
    (when timestamp
      (let [[_ content] (avl/nearest history <= timestamp)]
        (when content
          (editor/open-history content {:search @(rf/subscribe [:search])}))))))

(add-watch-db :goto-search [:search]
  (fn [_ _ _ search]
    (editor/goto-search search false)))

(add-watch-db :prefer-table-toggled [:prefer-table]
  (fn [_ _ _ prefer-table?]
    (editor/set-use-table prefer-table?)))

(def shift-interval 100)
(defn on-hit-shift []
  (if-not @(rf/subscribe [:search])
    (rf/dispatch [:set-search ""])
    (rf/dispatch [:set-search nil])))

(defn on-press-key [event]
  (let [{:keys [code key ctrl-key shift-key original repeat]} event time (current-time-millis)]
    (when-not repeat
      (when @(rf/subscribe [:navigation])
        (when (and (#{"ArrowUp"} code))
          (rf/dispatch [:navigate-up]))
        (when (and (#{"ArrowDown"} code))
          (rf/dispatch [:navigate-down]))
        (when (and (#{"Enter"} code))
          (rf/dispatch [:navigate-enter time]))
        (when (and (#{"Tab"} code))
          (rf/dispatch [:navigate-direct])))
      (if (= "ShiftLeft" code)
        (rf/dispatch [:shift-press time])
        (rf/dispatch [:shift-press nil]))
      (when (= "Escape" code)
        (rf/dispatch [:set-search nil])
        (rf/dispatch [:navigation-input nil]))
      (when (and ctrl-key (= "p" key))
        (.preventDefault original)
        (rf/dispatch [:toggle-navigation]))
      (when (and ctrl-key (= "j" key))
        (.preventDefault original)
        (rf/dispatch [:open-history-page]))
      (when (and ctrl-key (= "i" key))
        (.preventDefault original)
        (.click (js/document.getElementById "file-input")))
      (when @(rf/subscribe [:search])
        (when (or (= "Tab" key) (and (= "Enter" key) (nil? @(rf/subscribe [:navigation]))))
          (.preventDefault original)
          (editor/goto-search @(rf/subscribe [:search]) shift-key))
        (when (= "Backspace" key)
          (rf/dispatch [:swap-search splice-last]))
        (when (re-matches c/alphanumerical-regex key)
          (rf/dispatch [:swap-search #(str % (str/upper-case key))]))
        (when (= "=" key)
          (rf/dispatch [:swap-search #(str % "=")])))
      (when (and ctrl-key (= "s" key))
        (.preventDefault original)
        (save-editor-content))
      (editor/on-press-key event))))

(defn on-release-key [event]
  (let [{:keys [code repeat]} event time (current-time-millis)]
    (when-not repeat
      (when (= "ShiftLeft" code)
        (let [delta (- time (or @(rf/subscribe [:last-shift-press]) 0))]
          (when (> shift-interval delta)
            (on-hit-shift))))
      (editor/on-release-key event))))

(set! (.-onkeydown js/window) #(on-press-key (to-clj-event %)))
(set! (.-onkeyup js/window) #(on-release-key (to-clj-event %)))
(set! (.-onmousemove js/window) #(rf/dispatch [:mouse-move (to-clj-event %)]))
(set! (.-ontouchmove js/window) #(rf/dispatch [:mouse-move (to-clj-event %)]))
(set! (.-onmouseup js/window) #(rf/dispatch [:start-drag nil]))
(set! (.-ontouchend js/window) #(rf/dispatch [:start-drag nil]))
(set! (.-onresize js/window) #(rf/dispatch [:window-resize (.-innerWidth js/window) (.-innerHeight js/window)]))
(set! (.-onhashchange js/window) #(rf/dispatch [:hash-change (.-newURL %)]))
(set! (.-onblur js/window) #(editor/on-window-blur (to-clj-event %)))

(rf/dispatch-sync [:hash-change js/window.location.href])
; (js/setInterval (fn [] (save-editor-content)) 10000)

(defn on-js-reload [])
