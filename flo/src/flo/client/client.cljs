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
    [cljsjs.moment]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]
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
(def shift-interval 100)

(when-not js/document.initialized
  (set! (.-initialized js/document) true)
  (rf/dispatch-sync [:initialize (current-time-millis) init]))

(defn on-drag-start [event drag-btn-x]
  (let [clj-event (to-clj-event event)]
    (rf/dispatch [:start-drag {:mouse-x (:mouse-x clj-event) :btn-x drag-btn-x}])))

(defn drag-button []
  (let [timestamp (or @(rf/subscribe [:history-cursor]) @(rf/subscribe [:active-time-updated]))
        drag-btn-x @(rf/subscribe [:history-btn-x])]
    [:div {:style {:position         "absolute"
                   :height           "20px"
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
                   :left             drag-btn-x}
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
             :on-click #(rf/dispatch [:navigation-select note (current-time-millis) false])}
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
    [:div {:style {:max-height 504 :overflow-y "scroll"}}
     (for [[index note] (map-indexed vector @(rf/subscribe [:navigation-list]))]
       ^{:key [index (:name note)]}
       [navigation-btn note index])]]])

(defn image-upload []
  [:div#image-upload-outer
   {:on-click #(rf/dispatch [:toggle-image-upload])
    :style {:position "absolute"
            :top      0
            :bottom   0
            :left     0
            :right    0
            :z-index  10}}
   [:div {:style {:max-width 600
                  :min-height 300
                  :margin-top 100
                  :margin-left "auto"
                  :margin-right "auto"
                  :background-color "red"}}]])

(defn history-limit [label limit-ms]
  (let [hover? (r/atom false)
        press? (r/atom false)]
    (fn []
      [:div {:style {:float "left"
                     :width "24px"
                     :height "24px"
                     :font-size "11px"
                     :line-height "24px"
                     :text-align "center"
                     :color "rgba(255, 255, 255, 0.5)"
                     :user-select "none"
                     :font-family "Monospace"
                     :cursor "pointer"
                     :border-right "1px solid rgba(255, 255, 255, 0.3)"
                     :background-color
                     (cond
                       (or @press? (= limit-ms @(rf/subscribe [:history-limit]))) "rgba(0, 0, 0, 0.3)"
                       @hover? "rgba(0, 0, 0, 0.1)")}
             :on-mouse-over #(reset! hover? true)
             :on-mouse-out #(do (reset! hover? false) (reset! press? false))
             :on-mouse-down #(reset! press? true)
             :on-mouse-up #(reset! press? false)
             :on-click #(rf/dispatch [:set-history-limit limit-ms])
             :on-press #(rf/dispatch [:set-history-limit limit-ms])} label])))

(defn history-bar []
  [:div {:style {:height           "24px"
                 :background-color "#9e4446"
                 :flex-grow        "0"
                 :flex-shrink      "0"
                 :overflow         "hidden"}}
   [history-limit "H" (* 1000 60 60)]
   [history-limit "D" (* 1000 60 60 24)]
   [history-limit "W" (* 1000 60 60 24 7)]
   [history-limit "M" (* 1000 60 60 24 30)]
   [history-limit "Y" (* 1000 60 60 24 365)]
   [history-limit "A" (* 1000 60 60 24 10000)]
   [drag-button]])

(defn search-bar []
  [:div {:style {:height           "24px"
                 :background-color "#3DA1D2"
                 :line-height      "24px"
                 :color            "#FFF"
                 :font-family      "Monospace"
                 :font-size        "11px"
                 :text-indent      "10px"
                 :flex-grow        "0"
                 :flex-shrink      "0"}}
   (str "Search: " (pr-str @(rf/subscribe [:search])))])

(def ace-editor (r/atom nil))
(def ace-editor-ro (r/atom nil))

(defn file-uploaded [response]
  ; add [*b82b6c5e-6d44-11e9-a923-1681be663d3e]
  ; editor.session.insert(editor.getCursorPosition(), text)
  (doseq [{:keys [id]} response]
    (ace/insert-at-cursor @ace-editor (str "[*" id "]\n"))))

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
   (if @(rf/subscribe [:image-upload]) ^{:key "upl"} [image-upload])
   ^{:key "e1"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:show-read-only]) "none" "flex") :flex-direction "column"}} [:div#editor]]
   ^{:key "e2"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:show-read-only]) "flex" "none") :flex-direction "column"}} [:div#editor-read-only]]
   [search-bar]
   [history-bar]])

(r/render [app] (js/document.getElementById "app"))
(reset! ace-editor (ace/new-instance "editor"))
(reset! ace-editor-ro (ace/new-instance "editor-read-only"))
(ace/set-text @ace-editor (or @(rf/subscribe [:initial-content]) ""))
(ace/set-text @ace-editor-ro (or @(rf/subscribe [:initial-content]) ""))
(ace/set-read-only @ace-editor-ro true)

(defn on-click-link [editor {:keys [type value]}]
  (let [cats (into #{} (str/split type #"\."))]
    (when (set/subset? #{"tag" "declaration"} cats)
      (let [search (subs value 1 (dec (count value)))]
        (ace/navigate editor search {:declaration-only true})))
    (when (set/subset? #{"tag" "reference"} cats)
      (let [navigation (str/replace (subs value 2 (dec (count value))) ":" "@")
            [name search] (str/split navigation #"@")]
        (rf/dispatch [:set-search search])
        (rf/dispatch [:navigation-select name (current-time-millis) false])))
    (when (cats "link")
      (js/window.open value "_blank"))))

(doseq [editor [@ace-editor @ace-editor-ro]]
  (.on editor "linkClick"
    (fn [event-raw]
      (let [event {:type (.. event-raw -token -type) :value (.. event-raw -token -value)}]
        (on-click-link editor event)))))

(.on @ace-editor "changeSelection"
  #(if-not (.-autoChangeSelection @ace-editor)
     (rf/dispatch [:change-selection (ace/get-selection @ace-editor)])))

(rf/reg-fx :title (fn [title] (set! (.-title js/document) title)))
(rf/reg-fx :focus-editor
  (fn [_] (.focus @ace-editor)))

; copies all the contents of ace-editor-ro and displays them to ace-editor
(rf/reg-fx :set-session-from-ro
  (fn []
    (set! (.-autoChangeSelection @ace-editor) true)
    (ace/set-text @ace-editor (ace/get-text @ace-editor-ro))
    (js/setTimeout
      #(do (ace/set-selection @ace-editor (ace/get-selection @ace-editor-ro))
           (.focus @ace-editor)
           (set! (.-autoChangeSelection @ace-editor) false)) 0)))

(rf/reg-fx :show-editor
  (fn [[text search selection]]
    (set! (.-autoChangeSelection @ace-editor) true)
    (ace/set-text @ace-editor (or text ""))
    (js/setTimeout
      #(do (ace/set-selection @ace-editor selection)
           (ace/navigate @ace-editor search)
           (.focus @ace-editor)
           (set! (.-autoChangeSelection @ace-editor) false)) 0)))

(rf/reg-fx :show-editor-ro
  (fn [[text search selection]]
    (ace/set-text @ace-editor-ro (or text ""))
    (js/setTimeout
      #(do (ace/set-selection @ace-editor-ro selection)
           (ace/navigate @ace-editor-ro search)) 0)))

(defn toggle-navigation [editor]
  (let [cursor (js->clj (.getCursor (.getSelection editor)))
        row (get cursor "row")
        col (get cursor "column")
        line (.getLine (.-session editor) row)
        instances (find-all line #"\[=[^\]]+\]")
        instance (or (first (filter #(and (>= col (:start %)) (< col (:end %))) instances))
                     (first (filter #(and (>= col (:start %)) (<= col (:end %))) instances)))
        substr (:substr instance)
        substr-nx (if substr (str/replace (subs substr 2 (dec (count substr))) #":" "@"))]
    (rf/dispatch [:toggle-navigation substr-nx])))

(defn toggle-nav-command [editor]
  {:name "toggle-navigation"
   :exec #(toggle-navigation editor)
   :bindKey {:mac "cmd-p" :win "ctrl-p"}
   :readOnly true})

(def tab-command
  {:name "tab-command"
   :exec #(ace/indent-selection @ace-editor)
   :bindKey {:mac "tab" :win "tab"}
   :readOnly false})

(.addCommand (.-commands @ace-editor) (clj->js (toggle-nav-command @ace-editor)))
(.addCommand (.-commands @ace-editor) (clj->js tab-command))
(.addCommand (.-commands @ace-editor-ro) (clj->js (toggle-nav-command @ace-editor-ro)))

(add-watches-db :show-history [[:history-cursor] active-history [:history-direction]]
  (fn [_ _ _ [timestamp history direction]]
    (let [cmp (if (= :bkwd direction) >= <=)]
      (when timestamp
        (let [[_ note] (avl/nearest history cmp timestamp)]
          (ace/set-text @ace-editor-ro (or note ""))
          (ace/navigate @ace-editor-ro @(rf/subscribe [:search])))))))

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
  (if-not @(rf/subscribe [:search])
    (rf/dispatch [:set-search ""])
    (rf/dispatch [:set-search nil])))

(defn on-press-key
  [{:keys [code key ctrl-key shift-key original]}]
  (when @(rf/subscribe [:navigation])
    (when (and (#{"ArrowUp"} code))
      (rf/dispatch [:navigate-up]))
    (when (and (#{"ArrowDown"} code))
      (rf/dispatch [:navigate-down]))
    (when (and (#{"Enter"} code))
      (rf/dispatch [:navigate-in (current-time-millis)])))
  (if (= "ShiftLeft" code)
    (rf/dispatch [:shift-press (current-time-millis)])
    (rf/dispatch [:shift-press nil]))
  (when (= "Escape" code)
    (rf/dispatch [:set-search nil])
    (rf/dispatch [:navigation-input nil]))
  (when (and ctrl-key (= "p" key))
    (.preventDefault original)
    (toggle-navigation @ace-editor))
  (when (and ctrl-key (= "i" key))
    (.preventDefault original)
    (.click (js/document.getElementById "file-input")))
  (when @(rf/subscribe [:search])
    (when (or (= "Tab" key) (and (= "Enter" key) (nil? @(rf/subscribe [:navigation]))))
      (.preventDefault original)
      (if shift-key
        (doseq [e [@ace-editor @ace-editor-ro]] (ace/navigate e @(rf/subscribe [:search]) {"backwards" true}))
        (doseq [e [@ace-editor @ace-editor-ro]] (ace/navigate e @(rf/subscribe [:search])))))
    (when (= "Backspace" key)
      (rf/dispatch [:swap-search splice-last]))
    (when (re-matches #"^[A-Za-z0-9]$" key)
      (rf/dispatch [:swap-search #(str % (str/upper-case key))]))))

(defn on-release-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (let [delta (- (current-time-millis) (or @(rf/subscribe [:last-shift-press]) 0))]
      (when (> shift-interval delta)
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
