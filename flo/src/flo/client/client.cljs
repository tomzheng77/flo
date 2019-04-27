(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.ace :as ace]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
    [flo.client.store :refer [add-watches-db add-watch-db db active-history]]
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
  (rf/dispatch-sync [:initialize (current-time-millis) init]))

(def search (r/atom nil))

(defn on-drag-start [event drag-btn-x]
  (let [clj-event (to-clj-event event)]
    (rf/dispatch [:start-drag {:mouse-x (:mouse-x clj-event) :btn-x drag-btn-x}])))

(defn drag-button []
  (let [timestamp (or @(rf/subscribe [:history-cursor]) @(rf/subscribe [:active-time-updated]))
        drag-btn-x @(rf/subscribe [:history-btn-x])]
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

(defn navigation-btn [note index]
  (let [focus? (r/atom false)]
    (fn []
      [:div {:style {:width "auto" :height 24
                     :font-size 15
                     :text-indent 7
                     :padding-right 7
                     :line-height "24px"
                     :user-select "none"
                     :background-color (if (or @focus? (= index @(rf/subscribe [:navigation-index]))) "#c7cbd1")
                     :cursor "pointer"
                     :display "flex"
                     :flex-direction "row"}
             :on-mouse-over #(reset! focus? true)
             :on-mouse-out #(reset! focus? false)
             :on-click #(rf/dispatch [:navigation-select note (current-time-millis)])}
       [:div (:name note)]
       [:div {:style {:flex-grow 1}}]
       [:div {:style {:color "#777"}} (count (:content note))]
       [:div {:style {:color "#777"}} (.format (js/moment (:time-updated note)) "MM-DD hh:mm:ss")]
       ])))

(defn navigation []
  [:div#navigation-outer
   {:on-click #(rf/dispatch [:navigation-input nil])
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
              :on-change #(rf/dispatch [:navigation-input (-> % .-target .-value)])}]]
    [:div {:style {:height 4}}]
    (for [[index note] (map-indexed vector (take 20 @(rf/subscribe [:navigation-list])))]
      ^{:key [index (:name note)]}
      [navigation-btn note index])]])

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
   (str "Search: " (pr-str @(rf/subscribe [:search])))])

; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
(defn app []
  [:div#app-inner
   (if @(rf/subscribe [:navigation]) ^{:key "nav"} [navigation])
   ^{:key "e1"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:show-read-only]) "none" "flex") :flex-direction "column"}} [:div#editor]]
   ^{:key "e2"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:show-read-only]) "flex" "none") :flex-direction "column"}} [:div#editor-read-only]]
   [status-bar]
   [drag-bar]])

(r/render [app] (js/document.getElementById "app"))
(def ace-editor (r/atom (ace/new-instance "editor")))
(def ace-editor-ro (r/atom (ace/new-instance "editor-read-only")))
(ace/set-text @ace-editor (or @(rf/subscribe [:initial-content]) ""))
(ace/set-text @ace-editor-ro (or @(rf/subscribe [:initial-content]) ""))
(ace/set-read-only @ace-editor-ro true)

(rf/reg-fx :title (fn [title] (set! (.-title js/document) title)))
(rf/reg-fx :editor-focus
  (fn [_] (.focus @ace-editor)))
(rf/reg-fx :editor
  (fn [[text search]]
    (ace/set-text @ace-editor text)
    (ace/navigate @ace-editor search)
    (.focus @ace-editor)))
(rf/reg-fx :read-only
  (fn [[text search]]
    (ace/set-text @ace-editor-ro (or text ""))
    (ace/navigate @ace-editor-ro search)))

(def toggle-nav-command
  {:name "toggle-navigation"
   :exec #(rf/dispatch [:toggle-navigation])
   :bindKey {:mac "cmd-p" :win "ctrl-p"}})

(.addCommand (.-commands @ace-editor) (clj->js toggle-nav-command))
(.addCommand (.-commands @ace-editor-ro) (clj->js toggle-nav-command))

(add-watches-db :show-history [[:history-cursor] active-history]
  (fn [_ _ _ [timestamp history]]
    (when timestamp
      (let [[_ note] (avl/nearest history <= timestamp)]
        (ace/set-text @ace-editor-ro (or note ""))
        (ace/navigate @ace-editor-ro @(rf/subscribe [:search]))))))

(add-watches-db :disable-edit [[:search] [:history-cursor]]
  (fn [_ _ _ [search drag-timestamp]]
    (if (or search drag-timestamp)
      (ace/set-read-only @ace-editor true)
      (ace/set-read-only @ace-editor false))))

(add-watch-db :auto-search [:search]
  (fn [_ _ _ search]
    (ace/navigate @ace-editor search)
    (ace/navigate @ace-editor-ro search)))

(defn on-hit-shift []
  (if-not (= "" @(rf/subscribe [:search]))
    (rf/dispatch [:set-search ""])
    (rf/dispatch [:set-search nil])))

(defn on-press-key
  [event]
  (when @(rf/subscribe [:navigation])
    (when (and (#{"ArrowUp"} (:code event)))
      (rf/dispatch [:navigate-up]))
    (when (and (#{"ArrowDown"} (:code event)))
      (rf/dispatch [:navigate-down]))
    (when (and (#{"Enter"} (:code event)))
      (rf/dispatch [:navigate-in (current-time-millis)])))
  (if (= "ShiftLeft" (:code event))
    (rf/dispatch [:shift-press (current-time-millis)])
    (rf/dispatch [:shift-press nil]))
  (when (= "Escape" (:code event))
    (rf/dispatch [:set-search nil])
    (rf/dispatch [:navigation-input nil]))
  (when (and (:ctrl-key event) (= "p" (:key event)))
    (.preventDefault (:original event))
    (rf/dispatch [:toggle-navigation]))
  (when @(rf/subscribe [:search])
    (when (#{"Enter" "Tab"} (:key event))
      (.preventDefault (:original event))
      (if (:shift-key event)
        (for [e [@ace-editor @ace-editor-ro]] (ace/navigate e @(rf/subscribe [:search]) {"backwards" true}))
        (for [e [@ace-editor @ace-editor-ro]] (ace/navigate e @(rf/subscribe [:search])))))
    (when (= "Backspace" (:key event))
      (rf/dispatch [:swap-search splice-last]))
    (when (re-matches #"^[A-Za-z0-9]$" (:key event))
      (rf/dispatch [:swap-search #(str % (str/upper-case (:key event)))]))))

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
(set! (.-onmouseup js/window) #(rf/dispatch [:start-drag nil]))
(set! (.-ontouchend js/window) #(rf/dispatch [:start-drag nil]))
(set! (.-onresize js/window) #(rf/dispatch [:window-resize (.-innerWidth js/window) (.-innerHeight js/window)]))

(js/setInterval #(rf/dispatch [:editor-tick (ace/get-text @ace-editor) (current-time-millis)]) 1000)
(defn on-js-reload [])
