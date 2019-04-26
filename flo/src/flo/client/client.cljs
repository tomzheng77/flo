(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.ace :as ace]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
    [flo.client.store :refer [add-watches-db add-watch-db db]]
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

(defn on-drag-start [event drag-position]
  (let [clj-event (to-clj-event event)]
    (rf/dispatch [:drag-start {:x (:mouse-x clj-event) :y (:mouse-y clj-event) :position drag-position}])))

(defn drag-button []
  (let [timestamp (or @(rf/subscribe [:drag-timestamp]) @(rf/subscribe [:time-last-save]))
        drag-position (/ (* (- timestamp @(rf/subscribe [:time-start]))
                            (- @(rf/subscribe [:window-width]) @(rf/subscribe [:drag-btn-width])))
                         (- @(rf/subscribe [:time-last-save]) @(rf/subscribe [:time-start])))]
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
                   :margin-left      drag-position}
           :on-touch-start #(on-drag-start % drag-position)
           :on-mouse-down #(on-drag-start % drag-position)}
     (.format (js/moment timestamp) "YYYY-MM-DD h:mm:ss a")]))

(defn navigation []
  [:div {:style {:display (if @(rf/subscribe [:show-navigation]) "block" "none")
                 :position "absolute"
                 :left "auto"
                 :right "auto"
                 :top 100
                 :width 100
                 :height 100
                 :background-color "red"
                 :z-index 10}}
   (for [note-name @(rf/subscribe [:notes-list])]
     ^{:key note-name} [:div note-name])])

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
   [navigation]
   [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:drag-timestamp]) "none" "flex") :flex-direction "column"}} [:div#editor]]
   [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:drag-timestamp]) "flex" "none") :flex-direction "column"}} [:div#editor-read-only]]
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
            :exec #(rf/dispatch [:toggle-show-navigation])
            :bindKey {:mac "cmd-p" :win "ctrl-p"}}))

(defn navigate
  "navigates to the <index> occurrence of the <search> tag"
  ([search] (navigate search {}))
  ([search opts]
   (if (and search (not-empty search))
     (let [settings (clj->js (set/union {"caseSensitive" true "regExp" true "backwards" false} opts))]
       (.find @ace-editor (str "\\[=?" search "=?\\]") settings)))))

(defn last-before [list value]
  (loop [lo 0 hi (dec (count list)) best nil]
    (if-not (<= lo hi)
      (or best [nil nil])
      (let [mid (/ (+ lo hi) 2)]
        (if (>= value (nth list mid))
          (recur (inc mid) hi mid)
          (recur lo (dec mid) best))))))

(def last-history (atom nil))
(defn show-history [note]
  (when (not= @last-history note)
    (reset! last-history note)
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
    (reset! search nil))
  (when (and (:ctrl-key event) (= "p" (:key event)))
    (rf/dispatch [:toggle-show-navigation]))
  (when @search
    (println event)
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

(defn on-mouse-move
  [event]
  (let [mouse-x (:mouse-x event)
        active-drag @(rf/subscribe [:drag-start])]
    (if active-drag
      (let [dx (- mouse-x (:x active-drag))
            start-position (:position active-drag)
            width @(rf/subscribe [:window-width])]
        (let [drag-position      (min (max 0 (+ dx start-position)) (- width 80))
              max-drag-position  (- @(rf/subscribe [:window-width]) @(rf/subscribe [:drag-btn-width]))
              new-drag-timestamp (+ @(rf/subscribe [:time-start])
                                    (/ (* (- @(rf/subscribe [:time-last-save])
                                             @(rf/subscribe [:time-start])) drag-position)
                                       max-drag-position))]
          (if (= drag-position max-drag-position)
            (rf/dispatch [:set-drag-timestamp nil])
            (rf/dispatch [:set-drag-timestamp new-drag-timestamp])))))))

(set! (.-onkeydown js/window) #(on-press-key (to-clj-event %)))
(set! (.-onkeyup js/window) #(on-release-key (to-clj-event %)))
(set! (.-onmousemove js/window) (fn [event] (on-mouse-move (to-clj-event event))))
(set! (.-ontouchmove js/window) (fn [event] (on-mouse-move (to-clj-event event))))
(set! (.-onmouseup js/window) #(rf/dispatch [:set-drag-start nil]))
(set! (.-ontouchend js/window) #(rf/dispatch [:set-drag-start nil]))
(set! (.-onresize js/window) (rf/dispatch [:window-resize (.-innerWidth js/window) (.-innerHeight js/window)]))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" nil
        {:type :auto :packer (transit/get-transit-packer)})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(go-loop []
  (let [item (<! ch-chsk)]
    (match (:event item)
      [:chsk/recv [:flo/history [fid timestamp note]]]
      (rf/dispatch [:recv-history timestamp note])
      :else nil))
  (recur))

(defn save-content [content]
  (when (:open? @chsk-state)
    (rf/dispatch [:new-save (current-time-millis)])
    (chsk-send! [:flo/save [@(rf/subscribe [:file-id]) content]])))

(def last-save (atom nil))
(defn detect-change []
  (let [content (ace/get-text @ace-editor)]
    (locking last-save
      (when (nil? @last-save) (reset! last-save content))
      (when (not= content @last-save)
        (save-content content)
        (reset! last-save content)))))

(add-watch-db :drag-changed [:drag-timestamp]
  (fn [_ _ _ timestamp]
    (chsk-send! [:flo/seek [@(rf/subscribe [:file-id]) (js/Math.round timestamp)]])))

(js/setInterval detect-change 1000)

(defn on-js-reload [])
