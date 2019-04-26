(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.ace :as ace]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
    [flo.client.store :refer [add-watches-db add-watch-db db]]
    [flo.client.network]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [taoensso.sente.packers.transit :as transit]
    [clojure.string :as str]
    [clojure.data.avl :as avl]
    [cljsjs.jquery]
    [cljsjs.moment]
    [cljsjs.ace]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [clojure.set :as set]))

(enable-console-print!)
(defonce init
        (->> "init"
        (.getElementById js/document)
        (.-innerHTML)
        (b64/decodeString)
        (read-string)))

(when-not js/document.initialized
  (set! (.-initialized js/document) true)
  (rf/dispatch-sync [:initialize init]))

(def search (r/atom nil))

(defn on-drag-start [event drag-btn-x]
  (let [clj-event (to-clj-event event)]
    (rf/dispatch [:set-drag-start {:x (:mouse-x clj-event) :y (:mouse-y clj-event) :position drag-btn-x}])))

(defn drag-button []
  (let [timestamp (or @(rf/subscribe [:drag-timestamp]) @(rf/subscribe [:time-last-save]))
        drag-btn-x @(rf/subscribe [:drag-btn-x])]
    [:div {:style {:height           "100%"
                   :text-indent      "0"
                   :text-align       "center"
                   :background-color "#9e2023"
                   :font-family      "Monospace"
                   :padding-top      "2px"
                   :padding-bottom   "2px"
                   :color            "#FFF"
                   :cursor           "pointer"
                   :user-select      "none"
                   :line-height      "10px"
                   :font-size        8
                   :width            @(rf/subscribe [:drag-btn-width])
                   :margin-left      drag-btn-x}
           :on-touch-start #(on-drag-start % drag-btn-x)
           :on-mouse-down #(on-drag-start % drag-btn-x)}
     (.format (js/moment timestamp) "YYYY-MM-DD h:mm:ss a")]))

(defn navigation-btn [note]
  (let [focus? (r/atom false)]
    (fn []
      [:div {:style {:width "100%" :height 24
                     :font-size 15
                     :text-indent 7
                     :line-height "24px"
                     :user-select "none"
                     :background-color (if @focus? "#c7cbd1")
                     :cursor "pointer"
                     :display "flex"
                     :flex-direction "row"}
             :on-mouse-over #(reset! focus? true)
             :on-mouse-out #(reset! focus? false)}
       [:div (:name note)]
       [:div {:style {:flex-grow 1}}]
       [:div {:style {:color "#777"}} (.format (js/moment (:created-time note)) "MM-DD hh:mm:ss")]
       [:div {:style {:color "#777"}} (.format (js/moment (:updated-time note)) "MM-DD hh:mm:ss")]
       ])))

(defn navigation []
  [:div#navigation-outer
   {:on-click #(rf/dispatch [:set-navigation nil])
    :style {:position "absolute"
            :top      0
            :bottom   0
            :left     0
            :right    0
            :z-index  10}}
   [:div#navigation
    {:style {:width 600
             :margin-left "auto"
             :margin-right "auto"
             :padding 4
             :background-color "#ebedef"}}
    [:div {:style {:height 30 :background-color "white"}}
     [:input {:style {:border "none"
                      :line-height "30px"
                      :width "100%"
                      :height "100%"
                      :padding 0
                      :text-indent 7}
              :auto-focus true
              :value @(rf/subscribe [:navigation])
              :on-change #(rf/dispatch [:set-navigation (-> % .-target .-value)])}]]
    [:div {:style {:height 4}}]
    (for [note-name (take 20 @(rf/subscribe [:navigation-notes]))]
      ^{:key note-name}
      [navigation-btn note-name])]])

(defn drag-bar []
  [:div {:style {:height           "24px"
                 :background-color "#9e4446"
                 :flex-grow        "0"
                 :flex-shrink      "0"
                 :overflow         "hidden"}}
   [drag-button]])

(defn status-bar []
  [:div {:style {:height           "24px"
                 :background-color "#3DA1D2"
                 :line-height      "24px"
                 :color            "#FFF"
                 :font-family      "Monospace"
                 :font-size        "10px"
                 :text-indent      "10px"
                 :flex-grow        "0"
                 :flex-shrink      "0"}}
   (str "Search: " (pr-str @search))])

; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
(defn app []
  [:div#app-inner
   (if @(rf/subscribe [:navigation]) ^{:key "nav"} [navigation])
   ^{:key "e1"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:drag-timestamp]) "none" "flex") :flex-direction "column"}} [:div#editor]]
   ^{:key "e2"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:drag-timestamp]) "flex" "none") :flex-direction "column"}} [:div#editor-read-only]]
   [status-bar]
   [drag-bar]])

(r/render [app] (js/document.getElementById "app"))
(def ace-editor (r/atom (ace/new-instance "editor")))
(def ace-editor-ro (r/atom (ace/new-instance "editor-read-only")))
(ace/set-text @ace-editor (or @(rf/subscribe [:initial-content]) ""))
(ace/set-text @ace-editor-ro (or @(rf/subscribe [:initial-content]) ""))
(ace/set-read-only @ace-editor-ro true)

(.addCommand (.-commands @ace-editor)
  (clj->js {:name "toggle-navigation"
            :exec #(rf/dispatch [:toggle-navigation])
            :bindKey {:mac "cmd-p" :win "ctrl-p"}}))

(defn navigate
  "navigates to the <index> occurrence of the <search> tag"
  ([search] (navigate search {}))
  ([search opts]
   (if (and search (not-empty search))
     (let [settings (clj->js (set/union {"caseSensitive" true "regExp" true "backwards" false} opts))]
       (.find @ace-editor (str "\\[=?" search "=?\\]") settings)))))

(def ace-editor-ro-length (atom nil))
(defn show-history [note]
  (when (not= @ace-editor-ro-length (count note))
    (reset! ace-editor-ro-length (count note))
    (ace/set-text @ace-editor-ro (or note ""))))

(add-watches-db :show-history [[:drag-timestamp] [:history]]
  (fn [_ _ _ [timestamp history]]
    (when timestamp
      (let [[_ note] (avl/nearest history <= timestamp)]
        (show-history note)))))

(add-watches-db :disable-edit [[:search] [:drag-timestamp]]
  (fn [_ _ _ [search drag-timestamp]]
    (if (or search drag-timestamp)
      (ace/set-read-only @ace-editor true)
      (ace/set-read-only @ace-editor false))))

(add-watch-db :auto-search [:search]
  (fn [_ _ _ search] (navigate search)))

(defn on-hit-shift []
  (if-not (= "" @search)
    (reset! search "")
    (reset! search nil)))

(defn on-press-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (rf/dispatch [:shift-press (current-time-millis)])
    (rf/dispatch [:shift-press nil]))
  (when (= "Escape" (:code event))
    (reset! search nil)
    (rf/dispatch [:set-navigation nil]))
  (when (and (:ctrl-key event) (= "p" (:key event)))
    (.preventDefault (:original event))
    (rf/dispatch [:toggle-navigation]))
  (when @search
    (when (#{"Enter" "Tab"} (:key event))
      (.preventDefault (:original event))
      (if (:shift-key event)
        (navigate @search {"backwards" true})
        (navigate @search)))
    (when (= "Backspace" (:key event))
      (swap! search splice-last))
    (when (re-matches #"^[A-Za-z0-9]$" (:key event))
      (swap! search #(str % (str/upper-case (:key event)))))))

(defn on-release-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (let [delta (- (current-time-millis) (or @(rf/subscribe [:last-shift-press]) 0))]
      (when (> 200 delta)
        (on-hit-shift)))))

(set! (.-onkeydown js/window) #(on-press-key (to-clj-event %)))
(set! (.-onkeyup js/window) #(on-release-key (to-clj-event %)))
(set! (.-onmousemove js/window) #(rf/dispatch [:mouse-move (to-clj-event %)]))
(set! (.-ontouchmove js/window) #(rf/dispatch [:mouse-move (to-clj-event %)]))
(set! (.-onmouseup js/window) #(rf/dispatch [:set-drag-start nil]))
(set! (.-ontouchend js/window) #(rf/dispatch [:set-drag-start nil]))
(set! (.-onresize js/window) (rf/dispatch [:window-resize (.-innerWidth js/window) (.-innerHeight js/window)]))

(js/setInterval #(rf/dispatch [:edit (ace/get-text @ace-editor)]) 1000)
(defn on-js-reload [])
