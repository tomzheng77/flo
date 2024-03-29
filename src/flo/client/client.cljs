(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.editor.editor :as editor]
    [flo.client.functions :refer [current-time-millis splice-last]]
    [flo.client.store.store]
    [flo.client.store.watch :as w]
    [flo.client.store.history :as h]
    [flo.client.network]
    [flo.client.ui.navigation :as navigation]
    [flo.client.ui.bottom-bar :as bottom-bar]
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
    [flo.client.model.event :as e]))

(defn prefer-excel [s]
  (or (str/starts-with? s "\"<TBL>")
      (str/starts-with? s "<TBL>")))

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

; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
(defn app []
  [:div#app-inner
   [file-form]
   (if @(rf/subscribe [:navigation]) ^{:key "nav"} [navigation/component])
   [editor/view]
   [bottom-bar/component]])

(rd/render [app] (js/document.getElementById "app"))

(defn save-editor-content []
  (let [{:keys [name content]} (editor/get-name-and-content)]
    (when name
      (rf/dispatch [:save-if-changed name content]))))

(rf/reg-fx :focus-editor
  (fn [_]
    (editor/focus)))

(rf/reg-fx :accept-external-change
  (fn [note]
    (editor/accept-external-change note)))

(rf/reg-fx :open-note
  (fn [[note]]
    (save-editor-content)
    (let [editor-type (editor/preferred-editor-type (:content note))]
      (editor/open-note note {:use-editor editor-type}))))

(rf/reg-fx :preview-note
  (fn [[note]]
    (let [editor-type (editor/preferred-editor-type (:content note))]
      (editor/preview-note note {:use-editor editor-type}))))

(w/add-watches-db :open-history [[:active-note-name] [:history-cursor] h/active-history [:history-direction]]
  (fn [_ _ _ [name timestamp history direction]]
    (when-not timestamp
      (editor/close-history))
    (when timestamp
      (let [[_ content] (avl/nearest history <= timestamp)]
        (when content
          (let [editor-type (editor/preferred-editor-type content)]
            (editor/open-history name content {:use-editor editor-type})))))))

(rf/reg-fx :change-editor
  (fn [editor-type]
    (editor/change-editor editor-type)))

(w/add-watch-db :preview-closed [:navigation-index]
  (fn [_ _ _ navigation-index]
    (when (nil? navigation-index)
      (editor/close-preview))))

(defn on-press-key [event]
  (let [{:keys [code key ctrl-key shift-key original repeat]} event]
    (when-not repeat
      (when @(rf/subscribe [:navigation])
        (when (and (#{"ArrowUp"} code))
          (rf/dispatch [:navigate-up]))
        (when (and (#{"ArrowDown"} code))
          (rf/dispatch [:navigate-down]))
        (when (and (#{"Enter"} code))
          (rf/dispatch [:navigate-enter]))
        (when (and (#{"Tab"} code))
          (rf/dispatch [:navigate-direct])))
      (when (= "Escape" code)
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
      (when (and ctrl-key (= "s" key))
        (.preventDefault original)
        (save-editor-content))
      (editor/on-press-key event))))

(defn on-release-key [event]
  (let [{:keys [code repeat]} event time (current-time-millis)]
    (when-not repeat
      (editor/on-release-key event))))

(def skip-hash-change-to (r/atom nil))
(rf/reg-fx :set-title (fn [title] (set! (.-title js/document) title)))
(rf/reg-fx :set-hash
  (fn [hash]
    (reset! skip-hash-change-to hash)
    (set! (.. js/window -location -hash) hash)))

(set! (.-onkeydown js/window) #(on-press-key (e/from-dom-event %)))
(set! (.-onkeyup js/window) #(on-release-key (e/from-dom-event %)))
(set! (.-onmousemove js/window) #(rf/dispatch [:mouse-move (e/from-dom-event %)]))
(set! (.-ontouchmove js/window) #(rf/dispatch [:mouse-move (e/from-dom-event %)]))
(set! (.-onmouseup js/window) #(rf/dispatch [:start-drag nil]))
(set! (.-ontouchend js/window) #(rf/dispatch [:start-drag nil]))
(set! (.-onresize js/window) #(rf/dispatch [:window-resize (.-innerWidth js/window) (.-innerHeight js/window)]))
(set! (.-onblur js/window) #(editor/on-window-blur (e/from-dom-event %)))
(set! (.-onhashchange js/window)
  #(do (if-not (= (.-newURL %) @skip-hash-change-to)
         (rf/dispatch [:on-hash-change (.-newURL %)]))
       (reset! skip-hash-change-to nil)))

(rf/dispatch-sync [:on-hash-change js/window.location.href])
(js/setInterval (fn [] (when @(rf/subscribe [:autosave]) (save-editor-content))) 1000)

(defn on-js-reload [])
