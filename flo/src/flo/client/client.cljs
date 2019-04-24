(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.ace :as ace]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
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
    [clojure.set :as set]))

(enable-console-print!)
(defonce init
        (->> "init"
        (.getElementById js/document)
        (.-innerHTML)
        (b64/decodeString)
        (read-string)))

(def time-created (:time-created init))
(def time-updated (:time-updated init))
(def file-id (:file-id init))
(def initial-content (:content init))

(println "time created:" time-created)
(println "time updated:" time-updated)
(println "file:" file-id)
(println "initial content:" initial-content)

(defonce state
  (r/atom {:last-shift-press nil ; the time when the shift key was last pressed
           :search           nil ; the active label being searched, nil means no search
           :window-width     (.-innerWidth js/window)
           :drag-width       80
           :drag-timestamp   nil
           :drag-start       nil
           :history          (avl/sorted-map)
           :time-start       time-created
           :time-last-save   time-updated}))

(def time-start (r/cursor state [:time-start]))
(def time-last-save (r/cursor state [:time-last-save]))
(def window-width (r/cursor state [:window-width]))
(def drag-width (r/cursor state [:drag-width]))
(def drag-timestamp (r/cursor state [:drag-timestamp]))
(def drag-start (r/cursor state [:drag-start]))
(def history (r/cursor state [:history]))
(def search (r/cursor state [:search]))
(reset! time-start (min (- @time-last-save 1000) (max (- @time-last-save 10800000) time-created)))

(defn drag-button []
  (let [timestamp (or @drag-timestamp @time-last-save)
        drag-position (/ (* (- timestamp @time-start) (- @window-width @drag-width))
                         (- @time-last-save @time-start))]
    [:div {:style         {:height           "100%"
                           :text-indent      "0"
                           :text-align       "center"
                           ;:background-color "#ffe795"
                           :background-color "#9e2023"
                           :font-family      "Monospace"
                           :padding-top      "2px"
                           :padding-bottom   "2px"
                           :color            "#FFF"
                           :cursor           "pointer"
                           :user-select      "none"
                           :line-height      "10px"
                           :font-size        8
                           :width            @drag-width
                           :margin-left      drag-position}
           :on-mouse-down (fn [event]
                            (let [clj-event (to-clj-event event)]
                              (reset! drag-start {:x        (:mouse-x clj-event)
                                                  :y        (:mouse-y clj-event)
                                                  :position drag-position})))}
     (.format (js/moment timestamp) "YYYY-MM-DD h:mm:ss a")]))

(defn drag-bar []
  [:div {:style {:height           "24px"
                 ;:background-color "#ffdf70"
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
   (str "Search: " (pr-str (:search @state)))])

; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
(defn app []
  [:div#app-inner
   [:div {:style {:flex-grow 1 :display (if @drag-timestamp "none" "flex")
                  :flex-direction "column"}} [:div#editor]]
   [:div {:style {:flex-grow 1 :display (if @drag-timestamp "flex" "none")
                  :flex-direction "column"}} [:div#editor-read-only]]
   [status-bar]
   [drag-bar]])

(r/render [app] (js/document.getElementById "app"))
(def ace-editor (r/atom (ace/new-instance "editor")))
(def ace-editor-ro (r/atom (ace/new-instance "editor-read-only")))
(ace/set-text @ace-editor (or initial-content ""))
(ace/set-read-only @ace-editor-ro true)

;(println (new js/ace.Search))

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

(add-watch state :show-history
  (fn [_ _ old new]
    (if (or (not= (:drag-timestamp old) (:drag-timestamp new))
            (not= (count (:history old)) (count (:history new))))
      (let [timestamp (:drag-timestamp new) history (:history new)]
        (when timestamp
          (let [[_ note] (avl/nearest history <= timestamp)]
            (show-history note)))))))

(add-watch state :disable-edit
  (fn [_ _ old new]
    (if (or (not= (:search old) (:search new)) (not= (:drag-timestamp old) (:drag-timestamp new)))
      (if (or (:search new) (:drag-timestamp new))
        (ace/set-read-only @ace-editor true)
        (ace/set-read-only @ace-editor false)))))

(add-watch search :auto-search
  (fn [_ _ _ new] (navigate new)))

(reset! state @state)

(defn on-hit-shift []
  (if-not (= "" (:search @state))
    (reset! search "")
    (reset! search nil)))

(defn on-press-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (swap! state #(assoc % :last-shift-press (current-time-millis)))
    (swap! state #(assoc % :last-shift-press nil)))
  (when (= "Escape" (:code event))
    (reset! search nil))
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
    (let [delta (- (current-time-millis) (:last-shift-press @state 0))]
      (when (> 200 delta)
        (on-hit-shift)))))

(defn on-mouse-move
  [event]
  (let [mouse-x (:mouse-x event)
        active-drag @drag-start]
    (if active-drag
      (let [dx (- mouse-x (:x active-drag))
            start-position (:position active-drag)
            width @window-width]
        (let [drag-position (min (max 0 (+ dx start-position)) (- width 80))
              max-drag-position (- @window-width @drag-width)
              new-drag-timestamp (+ @time-start (/ (* (- @time-last-save @time-start) drag-position) max-drag-position))]
          (if (= drag-position max-drag-position)
            (reset! drag-timestamp nil)
            (reset! drag-timestamp new-drag-timestamp)))))))

(set! (.-onkeydown js/window) #(on-press-key (to-clj-event %)))
(set! (.-onkeyup js/window) #(on-release-key (to-clj-event %)))
(set! (.-onmousemove js/window) (fn [event] (on-mouse-move (to-clj-event event))))
(set! (.-onmouseup js/window) #(reset! drag-start nil))
(set! (.-onresize js/window) #(reset! window-width (.-innerWidth js/window)))

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
      (swap! history #(assoc % timestamp note))
      :else nil))
  (recur))

(defn save-content [content]
  (when (:open? @chsk-state)
    (reset! time-last-save (current-time-millis))
    (chsk-send! [:flo/save [file-id content]])))

(def last-save (atom nil))
(defn detect-change []
  (let [content (ace/get-text @ace-editor)]
    (locking last-save
      (when (nil? @last-save) (reset! last-save content))
      (when (not= content @last-save)
        (save-content content)
        (reset! last-save content)))))

(add-watch drag-timestamp :drag-changed
  (fn [_ _ _ timestamp]
    (chsk-send! [:flo/seek [file-id (js/Math.round timestamp)]])))

(js/setInterval detect-change 1000)

(defn on-js-reload [])
