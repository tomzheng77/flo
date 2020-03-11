(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.client-ace :as client-ace]
    [flo.client.client-excel :as client-excel]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
    [flo.client.store :refer [add-watches-db add-watch-db db active-history]]
    [flo.client.network]
    [flo.client.view :refer [navigation search-bar history-bar]]
    [flo.client.constants :as c]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [taoensso.sente.packers.transit :as transit]
    [clojure.string :as str]
    [clojure.data.avl :as avl]
    [cljsjs.moment]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [clojure.set :as set]
    [diff :as diff]))

(enable-console-print!)
(defonce init
         (->> "init"
              (.getElementById js/document)
              (.-innerHTML)
              (b64/decodeStringToByteArray)
              (js/stringFromUTF8Array)
              (read-string)))

(console-log (clj->js init))

(when-not js/document.initialized
  (set! (.-initialized js/document) true)
  (let [href js/window.location.href]
    (rf/dispatch-sync [:initialize (current-time-millis) init href])))

(def anti-forgery-field (:anti-forgery-field init))
(defn file-uploaded [response]
  ; add [*b82b6c5e-6d44-11e9-a923-1681be663d3e]
  ; editor.session.insert(editor.getCursorPosition(), text)
  (doseq [{:keys [id]} response]
    (client-ace/insert-image id)))

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

; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
(defn app []
  [:div#app-inner
   [file-form]
   (if @(rf/subscribe [:navigation]) ^{:key "nav"} [navigation])
   [:div#container-jexcel-editor]
   [client-excel/view]
   [client-ace/view]
   (if @(rf/subscribe [:search]) [search-bar])
   [history-bar]])

(r/render [app] (js/document.getElementById "app"))

(defn save-opened-note []
  (let [{:keys [name content]} (client-ace/get-name-and-content)]
    (when name
      (rf/dispatch [:editor-save name content]))))

(rf/reg-fx :set-hash (fn [hash] (set! (.. js/window -location -hash) hash)))
(rf/reg-fx :set-title (fn [title] (set! (.-title js/document) title)))
(rf/reg-fx :focus-editor (fn [_] (client-ace/focus)))

(rf/reg-fx :accept-external-change
  (fn [note]
    (client-ace/accept-external-change note)))

; copies all the contents of ace-editor-ro and displays them to ace-editor
(rf/reg-fx :open-note-after-preview
  (fn [note]
    (save-opened-note)
    (client-ace/open-note-after-preview note)))

(rf/reg-fx :open-note
  (fn [[note search]]
    (save-opened-note)
    (client-ace/open-note note search)))

(rf/reg-fx :preview-note
  (fn [[note search]]
    (client-ace/preview-note note search)))

(add-watches-db :open-history [[:history-cursor] active-history [:history-direction]]
  (fn [_ _ _ [timestamp history direction]]
    (when timestamp
      (let [[_ content] (avl/nearest history <= timestamp)]
        (when content
          (client-ace/open-history content @(rf/subscribe [:search])))))))

(add-watches-db :disable-edit [[:search] [:history-cursor]]
  (fn [_ _ _ [search drag-timestamp]]
    (client-ace/set-editable (not (or search drag-timestamp)))))

(add-watch-db :auto-search [:search]
  (fn [_ _ _ search]
    (client-ace/next-search search false)))

(def shift-interval 100)
(defn on-hit-shift []
  (if-not @(rf/subscribe [:search])
    (rf/dispatch [:set-search ""])
    (rf/dispatch [:set-search nil])))

(defn on-press-key [event]
  (let [{:keys [code key ctrl-key shift-key original]} event]
    (when @(rf/subscribe [:navigation])
      (when (and (#{"ArrowUp"} code))
        (rf/dispatch [:navigate-up]))
      (when (and (#{"ArrowDown"} code))
        (rf/dispatch [:navigate-down]))
      (when (and (#{"Enter"} code))
        (rf/dispatch [:navigate-enter (current-time-millis)]))
      (when (and (#{"Tab"} code))
        (rf/dispatch [:navigate-direct])))
    (if (= "ShiftLeft" code)
      (rf/dispatch [:shift-press (current-time-millis)])
      (rf/dispatch [:shift-press nil]))
    (when (= "Escape" code)
      (rf/dispatch [:set-search nil])
      (rf/dispatch [:navigation-input nil]))
    (when (and ctrl-key (= "j" key))
      (.preventDefault original)
      (rf/dispatch [:open-history-page]))
    (when @(rf/subscribe [:search])
      (when (or (= "Tab" key) (and (= "Enter" key) (nil? @(rf/subscribe [:navigation]))))
        (.preventDefault original)
        (client-ace/next-search @(rf/subscribe [:search]) shift-key))
      (when (= "Backspace" key)
        (rf/dispatch [:swap-search splice-last]))
      (when (re-matches c/alphanumerical-regex key)
        (rf/dispatch [:swap-search #(str % (str/upper-case key))]))
      (when (= "=" key)
        (rf/dispatch [:swap-search #(str % "=")])))
    (when (and ctrl-key (= "s" key))
      (.preventDefault original)
      (save-opened-note))
    (client-ace/on-press-key event)))

(defn on-release-key [event]
  (let [{:keys [code]} event]
    (when (= "ShiftLeft" code)
      (let [delta (- (current-time-millis) (or @(rf/subscribe [:last-shift-press]) 0))]
        (when (> shift-interval delta)
          (on-hit-shift)))))
  (client-ace/on-release-key event))

(set! (.-onkeydown js/window) #(on-press-key (to-clj-event %)))
(set! (.-onkeyup js/window) #(on-release-key (to-clj-event %)))
(set! (.-onmousemove js/window) #(rf/dispatch [:mouse-move (to-clj-event %)]))
(set! (.-ontouchmove js/window) #(rf/dispatch [:mouse-move (to-clj-event %)]))
(set! (.-onmouseup js/window) #(rf/dispatch [:start-drag nil]))
(set! (.-ontouchend js/window) #(rf/dispatch [:start-drag nil]))
(set! (.-onresize js/window) #(rf/dispatch [:window-resize (.-innerWidth js/window) (.-innerHeight js/window)]))
(set! (.-onhashchange js/window) #(rf/dispatch [:hash-change (.-newURL %)]))
(set! (.-onblur js/window) #(client-ace/on-blur))

(rf/dispatch-sync [:hash-change js/window.location.href])
(js/setInterval (fn [] (save-opened-note)) 1000)

(defn on-js-reload [])
