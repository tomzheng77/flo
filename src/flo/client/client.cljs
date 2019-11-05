(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.ace.ace :as ace]
    [flo.client.ace.ace-clickables]
    [flo.client.ace.ace-colors]
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

(def anti-forgery-field (:anti-forgery-field init))
(def shift-interval 100)

(when-not js/document.initialized
  (set! (.-initialized js/document) true)
  (let [href js/window.location.href]
    (rf/dispatch-sync [:initialize (current-time-millis) init href])))

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

; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
(defn app []
  [:div#app-inner
   [file-form]
   (if @(rf/subscribe [:navigation]) ^{:key "nav"} [navigation])
   ^{:key "e1"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:read-only-visible]) "none" "flex") :flex-direction "column"}} [:div#editor]]
   ^{:key "e2"} [:div {:style {:flex-grow 1 :display (if @(rf/subscribe [:read-only-visible]) "flex" "none") :flex-direction "column"}} [:div#editor-read-only]]
   (if @(rf/subscribe [:search]) [search-bar])
   [history-bar]])

(r/render [app] (js/document.getElementById "app"))
(reset! ace-editor (ace/new-instance "editor"))
(reset! ace-editor-ro (ace/new-instance "editor-read-only"))
(ace/set-text @ace-editor (or @(rf/subscribe [:initial-content]) ""))
(ace/set-text @ace-editor-ro (or @(rf/subscribe [:initial-content]) ""))
(ace/set-read-only @ace-editor-ro true)

(.on @ace-editor "change" #(if-not (.-autoChange @ace-editor) (rf/dispatch [:change %])))
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
            end-col (if (>= 1 (count lines)) (+ col (count (first lines))) (count (last lines)))
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
              (recur (next remain) end-row end-col deltas))))))

(rf/reg-fx :refresh-editor
  (fn [content]
    (let [changes (js->clj (.diffChars diff (ace/get-text @ace-editor) content) :keywordize-keys true)
          deltas (changes->deltas changes)]
      (ace/apply-deltas @ace-editor deltas))))

; copies all the contents of ace-editor-ro and displays them to ace-editor
(rf/reg-fx :reset-editor-from-ro
  (fn [name]
    (when @ace-editor-note-name
      (rf/dispatch [:editor-tick @ace-editor-note-name (ace/get-text @ace-editor)]))
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
      (rf/dispatch [:editor-tick @ace-editor-note-name (ace/get-text @ace-editor)]))
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
  (let [token (:substr (first-at (find-all line c/declaration-regex) col))]
    (if token (subs token 1 (dec (count token))))))

; [TAG-SYNTAX]
(defn tag-definition-at [line col]
  (let [token (:substr (first-at (find-all line c/definition-regex) col))]
    (if token (subs token 1 (dec (count token))))))

(defn tag-reference-at [line col]
  (let [token (:substr (first-at (find-all line c/reference-regex) col))]
    (if token (subs token 1 (dec (count token))))))

; [TAG-SYNTAX]
(defn toggle-navigation [editor]
  (let [cursor (ace/get-cursor editor)
        row (:row cursor)
        col (:column cursor)
        line (.getLine (.-session editor) row)
        declaration (tag-declaration-at line col)
        definition (tag-definition-at line col)
        reference (tag-reference-at line col)]
    (cond
      declaration (rf/dispatch [:set-search (c/declaration-to-definition declaration)])
      definition (rf/dispatch [:set-search (c/definition-to-declaration definition)])
      reference (rf/dispatch [:navigate-direct reference])
      true (rf/dispatch [:toggle-navigation]))))

; [TAG-SYNTAX]
(defn next-tag [editor direction]
  (if (= :up direction)
    (ace/navigate editor c/any-navigation-inner {:backwards true})
    (ace/navigate editor c/any-navigation-inner)))

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
      (rf/dispatch [:navigate-direct])))
  (if (= "ShiftLeft" code)
    (rf/dispatch [:shift-press (current-time-millis)])
    (rf/dispatch [:shift-press nil]))
  (when (= "Escape" code)
    (rf/dispatch [:set-search nil])
    (rf/dispatch [:navigation-input nil]))
  (when (and ctrl-key (= "j" key))
    (.preventDefault original)
    (rf/dispatch [:open-history]))
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
    (when (re-matches c/alphanumerical-regex key)
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
        (ace/focus-if-not-search @ace-editor-ro)
        (ace/focus-if-not-search @ace-editor))))
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
(set! (.-onblur js/window)
  (fn []
    (ace/hide-clickables @ace-editor)
    (ace/hide-clickables @ace-editor-ro)))

(rf/dispatch-sync [:hash-change js/window.location.href])
(js/setInterval #(rf/dispatch [:editor-tick @ace-editor-note-name (ace/get-text @ace-editor)]) 1000)
(defn on-js-reload [])
