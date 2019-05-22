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

(def anti-forgery-field (:anti-forgery-field init))
(def shift-interval 100)

(when-not js/document.initialized
  (set! (.-initialized js/document) true)
  (let [href js/window.location.href]
    (rf/dispatch-sync [:initialize (current-time-millis) init href])))

(defn on-drag-start [event drag-btn-x]
  (let [clj-event (to-clj-event event)]
    (rf/dispatch [:start-drag {:mouse-x (:mouse-x clj-event) :btn-x drag-btn-x}])))

(defn history-button []
  (let [timestamp (or @(rf/subscribe [:history-cursor]) @(rf/subscribe [:active-time-updated]))
        drag-btn-x @(rf/subscribe [:history-button-x])]
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

(defn navigation-row [note index]
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
             :on-click #(rf/dispatch [:open-note note (current-time-millis) false])}
       (if (:ntag note) [:div {:style {:background-color "rgba(0,0,0,0.1)"
                                       :text-align "center"
                                       :font-family "Monospace"
                                       :font-size 12
                                       :text-indent 0
                                       :padding-left 10
                                       :padding-right 10}} (:ntag note)])
       [:div (:name note)]
       [:div {:style {:flex-grow 1}}]
       [:div {:style {:color "#777"}} (count (:content note)) " chars"]
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
       [navigation-row note index])]]])

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
      [:div {:style {:width "24px"
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
             :on-click #(rf/dispatch [:set-history-limit limit-ms])} label])))

(defn history-bar []
  [:div {:style {:height           "24px"
                 :background-color "#9e4446"
                 :flex-grow        "0"
                 :flex-shrink      "0"
                 :overflow         "hidden"
                 :display          "flex"
                 :align-items      "center"}}
   [history-limit "H" (* 1000 60 60)]
   [history-limit "D" (* 1000 60 60 24)]
   [history-limit "W" (* 1000 60 60 24 7)]
   [history-limit "M" (* 1000 60 60 24 30)]
   [history-limit "Y" (* 1000 60 60 24 365)]
   [history-limit "A" (* 1000 60 60 24 10000)]
   [history-button]])

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

(def ace-editor-note-name (r/atom nil))
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

(defn global-sidebar []
  [:div {:style {:position "absolute"
                 :right 0
                 :top 0
                 :bottom 0
                 :min-width 100
                 :pointer-events "none"
                 :display "flex"
                 :flex-direction "column"
                 :align-items "flex-end"
                 :padding-right 20
                 :padding-top 20}}
   (doall (for [global @(rf/subscribe [:globals])]
     [:div {:style {:display "flex"
                    :flex-direction "row"
                    :align-items "center"
                    :margin-bottom 10}}
      [:div {:style {:color "white"
                     :margin-right 5}} (:name (:note global))]
      [:div {:style {:color "#3DA1D2"
                     :font-weight "bold"
                     :font-family "Go-Mono"
                     :cursor "pointer"
                     :pointer-events "auto"
                     :padding 3
                     :border-radius 3
                     :border "1px solid gray"}} (:substr global)]]))])

; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
(defn app []
  [:div#app-inner
   [file-form]
   (if @(rf/subscribe [:navigation]) ^{:key "nav"} [navigation])
   (if @(rf/subscribe [:image-upload]) ^{:key "upl"} [image-upload])
   ^{:key "e1"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:read-only-visible]) "none" "flex") :flex-direction "column"}} [:div#editor]]
   ^{:key "e2"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:read-only-visible]) "flex" "none") :flex-direction "column"}} [:div#editor-read-only]]
   (if @(rf/subscribe [:search]) [search-bar])
   [global-sidebar]
   [history-bar]])

(r/render [app] (js/document.getElementById "app"))
(reset! ace-editor (ace/new-instance "editor"))
(reset! ace-editor-ro (ace/new-instance "editor-read-only"))
(ace/set-text @ace-editor (or @(rf/subscribe [:initial-content]) ""))
(ace/set-text @ace-editor-ro (or @(rf/subscribe [:initial-content]) ""))
(ace/set-read-only @ace-editor-ro true)

(.on @ace-editor "changeSelection"
  #(if-not (.-autoChangeSelection @ace-editor)
     (rf/dispatch [:change-selection (ace/get-selection @ace-editor)])))

(rf/reg-fx :set-hash (fn [hash] (set! (.. js/window -location -hash) hash)))
(rf/reg-fx :set-title (fn [title] (set! (.-title js/document) title)))
(rf/reg-fx :focus-editor
  (fn [_] (.focus @ace-editor)))

; https://stackoverflow.com/questions/18050128/applydeltas-in-ace-editor
; :action :insert
; :action :remove
(defn changes->deltas [changes]
  (loop [remain changes row 0 col 0 deltas []]
    (if (empty? remain)
      deltas
      (let [head (first remain)
            lines (js->clj (js/splitLines (str/replace (:value head) #"\r\n" "\n")))
            end-row (+ row (dec (count lines)))
            end-col (if (>= 1 (count lines))
                      (+ col (count (first lines)))
                      (count (last lines)))
            is-add (true? (:added head))
            is-remove (true? (:removed head))]
        (cond is-add
              (recur (next remain)
                     end-row
                     end-col
                     (conj deltas
                       {:start {:row row :column col}
                        :end {:row end-row :column end-col}
                        :action "insert" :lines lines}))
              is-remove
              (recur (next remain)
                     end-row
                     end-col
                     (conj deltas
                       {:start {:row row :column col}
                        :end {:row end-row :column end-col}
                        :action "remove" :lines lines}))
              true
              (recur (next remain)
                     end-row
                     end-col
                     deltas))))))

(rf/reg-fx :refresh-editor
  (fn [content]
    (let [changes (js->clj (.diffChars diff (ace/get-text @ace-editor) content) :keywordize-keys true)
          deltas (changes->deltas changes)]
      (ace/apply-deltas @ace-editor deltas))))

; copies all the contents of ace-editor-ro and displays them to ace-editor
(rf/reg-fx :reset-editor-from-ro
  (fn [name]
    (when @ace-editor-note-name
      (rf/dispatch [:editor-tick @ace-editor-note-name (ace/get-text @ace-editor) (current-time-millis)]))
    (reset! ace-editor-note-name name)
    (set! (.-autoChangeSelection @ace-editor) true)
    (ace/set-text @ace-editor (ace/get-text @ace-editor-ro))
    (js/setTimeout
      #(do (ace/set-selection @ace-editor (ace/get-selection @ace-editor-ro))
           (.focus @ace-editor)
           (set! (.-autoChangeSelection @ace-editor) false)) 0)))

(rf/reg-fx :reset-editor
  (fn [[name text search selection]]
    (when @ace-editor-note-name
      (rf/dispatch [:editor-tick @ace-editor-note-name (ace/get-text @ace-editor) (current-time-millis)]))
    (reset! ace-editor-note-name name)
    (set! (.-autoChangeSelection @ace-editor) true)
    (ace/set-text @ace-editor (or text ""))
    (js/setTimeout
      #(do (ace/set-selection @ace-editor selection)
           (ace/navigate @ace-editor search)
           (.focus @ace-editor)
           (set! (.-autoChangeSelection @ace-editor) false)) 0)))

(rf/reg-fx :reset-editor-ro
  (fn [[text search selection]]
    (ace/set-text @ace-editor-ro (or text ""))
    (js/setTimeout
      #(do (ace/set-selection @ace-editor-ro selection)
           (ace/navigate @ace-editor-ro search)) 0)))

(defn first-at [search-results index]
  (or (first (filter #(and (>= index (:start %)) (< index (:end %))) search-results))
      (first (filter #(and (>= index (:start %)) (<= index (:end %))) search-results))))

; [TAG-SYNTAX]
(defn tag-declaration-at [line col]
  (let [token (:substr (first-at (find-all line #"\[!?[A-Z0-9]+\]") col))]
    (if token (subs token 1 (dec (count token))))))

; [TAG-SYNTAX]
(defn tag-definition-at [line col]
  (let [token (:substr (first-at (find-all line #"\[!?[A-Z0-9]+=\]") col))]
    (if token (subs token 1 (dec (count token))))))

(defn tag-reference-at [line col]
  (let [token (:substr (first-at (find-all line #"\[[A-Z0-9]+@[A-Z0-9]*\]") col))]
    (if token (subs token 1 (dec (count token))))))

(defn remove-global [str]
  (if (str/starts-with? str "$") (subs str 1) str))

; [TAG-SYNTAX]
(defn toggle-navigation [editor]
  (let [cursor (js->clj (.getCursor (.getSelection editor)))
        row (get cursor "row")
        col (get cursor "column")
        line (.getLine (.-session editor) row)
        declaration (tag-declaration-at line col)
        definition (tag-definition-at line col)
        reference (tag-reference-at line col)]
    (cond
      declaration (rf/dispatch [:set-search (remove-global (str declaration "="))])
      definition (rf/dispatch [:set-search (remove-global (subs definition 0 (dec (count definition))))])
      reference (rf/dispatch [:navigate-direct (current-time-millis) reference])
      true (rf/dispatch [:toggle-navigation]))))

; [TAG-SYNTAX]
(defn next-tag [editor direction]
  (if (= :up direction)
    (ace/navigate editor "!?[A-Z0-9]+(@[A-Z0-9]*|=)?" {:backwards true})
    (ace/navigate editor "!?[A-Z0-9]+(@[A-Z0-9]*|=)?")))

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

(defn ctrl-up-command [editor]
  {:name "up-tag"
   :exec #(next-tag editor :up)
   :bindKey {:mac "cmd-up" :win "ctrl-up"}
   :readOnly true})

(defn ctrl-down-command [editor]
  {:name "down-tag"
   :exec #(next-tag editor :down)
   :bindKey {:mac "cmd-down" :win "ctrl-down"}
   :readOnly true})

(.addCommand (.-commands @ace-editor) (clj->js (toggle-nav-command @ace-editor)))
(.addCommand (.-commands @ace-editor) (clj->js tab-command))
(.addCommand (.-commands @ace-editor-ro) (clj->js (toggle-nav-command @ace-editor-ro)))

(.addCommand (.-commands @ace-editor) (clj->js (ctrl-up-command @ace-editor)))
(.addCommand (.-commands @ace-editor) (clj->js (ctrl-down-command @ace-editor)))
(.addCommand (.-commands @ace-editor-ro) (clj->js (ctrl-up-command @ace-editor-ro)))
(.addCommand (.-commands @ace-editor-ro) (clj->js (ctrl-down-command @ace-editor-ro)))

(add-watches-db :show-history [[:history-cursor] active-history [:history-direction]]
  (fn [_ _ _ [timestamp history direction]]
    (let [cmp (if (= :bkwd direction) >= <=)]
      (when timestamp
        (let [[latest-timestamp _] (avl/nearest history <= (current-time-millis))
              [_ note] (avl/nearest history cmp timestamp)]
          (if (and (= :bkwd direction) (< latest-timestamp timestamp))
            (ace/set-text @ace-editor-ro (ace/get-text @ace-editor))
            (ace/set-text @ace-editor-ro (or note "")))
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
  (when (= "Control" key)
    (ace/show-clickables @ace-editor)
    (ace/show-clickables @ace-editor-ro))
  (when @(rf/subscribe [:navigation])
    (when (and (#{"ArrowUp"} code))
      (rf/dispatch [:navigate-up]))
    (when (and (#{"ArrowDown"} code))
      (rf/dispatch [:navigate-down]))
    (when (and (#{"Enter"} code))
      (rf/dispatch [:navigate-enter (current-time-millis)]))
    (when (and (#{"Tab"} code))
      (rf/dispatch [:navigate-direct (current-time-millis)])))
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
        (doseq [e [@ace-editor @ace-editor-ro]] (ace/navigate e @(rf/subscribe [:search]) {:backwards true}))
        (doseq [e [@ace-editor @ace-editor-ro]] (ace/navigate e @(rf/subscribe [:search])))))
    (when (= "Backspace" key)
      (rf/dispatch [:swap-search splice-last]))
    (when (re-matches #"^[A-Za-z0-9]$" key)
      (rf/dispatch [:swap-search #(str % (str/upper-case key))]))
    (when (= "=" key)
      (rf/dispatch [:swap-search #(str % "=")]))))

(defn on-release-key
  [{:keys [code key ctrl-key shift-key original]}]
  (when (= "Control" key)
    (ace/hide-clickables @ace-editor)
    (ace/hide-clickables @ace-editor-ro)
    (when (not @(rf/subscribe [:navigation]))
      (if @(rf/subscribe [:read-only-visible])
        (.focus @ace-editor-ro)
        (.focus @ace-editor))))
  (when (= "ShiftLeft" code)
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
(set! (.-onhashchange js/window) #(rf/dispatch [:hash-change (.-newURL %)]))
(rf/dispatch-sync [:hash-change js/window.location.href])

(js/setInterval #(rf/dispatch [:editor-tick @ace-editor-note-name (ace/get-text @ace-editor) (current-time-millis)]) 1000)
(defn on-js-reload [])
