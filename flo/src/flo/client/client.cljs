(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.quill :as quill]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all
                                  intersects remove-overlaps to-clj-event]]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [taoensso.sente.packers.transit :as transit]
    [clojure.string :as str]
    [clojure.data.avl :as avl]
    [cljsjs.jquery]
    [cljsjs.quill]
    [cljsjs.moment]
    [quill-image-resize-module]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]))

(enable-console-print!)
(defonce configuration
   (->> "init"
        (.getElementById js/document)
        (.-innerHTML)
        (b64/decodeString)
        (read-string)))

(def time-created (:time-created configuration))
(def time-updated (:time-updated configuration))
(def time-last-save (r/atom time-updated))
(def file-id (:file-id configuration))
(def initial-content (:content configuration))

(println "time created:" time-created)
(println "time updated:" time-updated)
(println "time last save:" time-last-save)
(println "file:" file-id)
(println "initial content:" initial-content)

(def example-state
  {:last-shift-press 0                                      ; the time when the shift key was last pressed
   :search           "AA"                                   ; the active label being searched, nil means no search
   :content          {}})                                   ; the current contents of the editor

(defonce state
  (r/atom {:last-shift-press nil
           :search           nil
           :select           nil
           :content          nil}))

(def window-width (r/atom (.-innerWidth js/window)))
(def drag-width (r/atom 80))
(def drag-timestamp (r/atom nil))
(def drag-start (r/atom nil))
(def history (r/atom []))

(defn drag-button []
  (let [timestamp (or @drag-timestamp @time-last-save)
        drag-position (/ (* (- timestamp time-created) (- @window-width @drag-width))
                         (- @time-last-save time-created))]
    [:div {:style {:height           "100%"
                   :text-indent      "0"
                   :text-align       "center"
                   :background-color "yellow"
                   :color            "black"
                   :cursor           "pointer"
                   :user-select      "none"
                   :line-height      "15px"
                   :font-size        8
                   :width            @drag-width
                   :margin-left      drag-position}
           :on-mouse-down (fn [event]
                            (let [clj-event (to-clj-event event)]
                              (reset! drag-start {:x (:mouse-x clj-event)
                                                  :y (:mouse-y clj-event)
                                                  :position drag-position})))}
     (.format (js/moment timestamp) "YYYY-MM-DD h:mm:ss a")]))

(defn drag-bar []
  [:div {:style {:height           "30px"
                 :background-color "red"
                 :color            "#FFF"
                 :font-family      "Monospace"
                 :text-indent      "10px"
                 :flex-grow        "0"
                 :flex-shrink      "0"}}
   [drag-button]])

(defn status-bar []
  [:div {:style {:height           "30px"
                 :background-color "#3DA1D2"
                 :line-height      "30px"
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
   [:div#editor]
   [drag-bar]
   [status-bar]])

(r/render [app] (js/document.getElementById "app"))
(quill/new-instance)
(quill/set-content initial-content)

(defn navigate [search select]
  (if (empty? search)
    (quill/set-selection)
    (let [text (quill/get-text)]
      (loop [s search]
        (if (not-empty s)
          (let [occur      (concat (find-all text (str "[" s "]"))
                                   (find-all text (str "[" s "=]"))
                                   (find-all text (str "[=" s "]"))
                                   (find-all text (str "[=" s "=]"))
                                   (find-all text (str "[" s)))
                occur-uniq (sort-by :start (remove-overlaps occur))
                target     (and (not-empty occur-uniq)
                                (nth occur-uniq (mod select (count occur-uniq))))]
            (if-not target
              (recur (splice-last s))
              (quill/goto (:index target) (:length target)))))))))

(add-watch state :auto-search
  (fn [_ _ old new]
    (if (or (not= (:search old) (:search new))
            (not= (:select old) (:select new)))
      (if (:search new)
        (navigate (:search new) (:select new 0))))))

(reset! state @state)

(defn on-hit-shift []
  (if-not (= "" (:search @state))
    (do (swap! state #(-> % (assoc :search "") (assoc :select 0)))
        (quill/disable-edit))
    (do (swap! state #(-> % (assoc :search nil) (assoc :select 0)))
        (quill/enable-edit)
        (quill/focus)
        (quill/set-cursor-at-selection))))

(defn on-press-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (swap! state #(assoc % :last-shift-press (current-time-millis)))
    (swap! state #(assoc % :last-shift-press nil)))
  (when (= "Escape" (:code event))
    (swap! state #(-> % (assoc :search nil) (assoc :select 0)))
    (quill/enable-edit)
    (quill/focus)
    (quill/set-cursor-at-selection))
  (when (:search @state)
    (when (= "Tab" (:key event))
      (swap! state #(-> % (assoc :select (inc (:select %)))))
      (.preventDefault (:original event)))
    (when (= "Backspace" (:key event))
      (swap! state #(-> % (assoc :search (splice-last (:search %)))
                          (assoc :select 0))))
    (when (re-matches #"^[A-Za-z0-9]$" (:key event))
      (swap! state #(-> % (assoc :search (str (:search %) (str/upper-case (:key event))))
                          (assoc :select 0)))))
  (when (and (:ctrl-key event) (= "h" (:key event)))
    (quill/highlight-tags)
    (.preventDefault (:original event))))

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
              new-drag-timestamp (+ time-created (/ (* (- @time-last-save time-created) drag-position) max-drag-position))]
          (reset! drag-timestamp new-drag-timestamp))))))

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

(def history (r/atom (avl/sorted-map)))
(add-watch history :history-changed
  (fn [_ _ _ new]
    (let [size (count new)]
      (if (> size 10)
        (println (nth new 9))))))

(go-loop []
  (let [item (<! ch-chsk)]
    (match (:event item)
      [:chsk/recv [:flo/history [fid timestamp note]]]
      (swap! history #(assoc % timestamp note))
      :else nil))
  (recur))

(defn save-content [content]
  (if (:open? @chsk-state)
    (chsk-send! [:flo/save [file-id content]])))

(def last-save (atom nil))
(defn detect-change []
  (let [content (quill/get-content)]
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
