(ns flo.client.client-ace
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.jexcel.jexcel :as jexcel]
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

(def anti-forgery-field (r/atom nil))
(def in-read-only-mode (r/atom nil))
(def ace-editor-note-name (r/atom nil))
(def ace-editor (r/atom nil))
(def ace-editor-ro (r/atom nil))

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
(defn next-tag [editor direction]
  (if (= :up direction)
    (ace/navigate editor c/any-navigation-inner {:backwards true})
    (ace/navigate editor c/any-navigation-inner)))

(defn toggle-nav-command [editor]
  {:name "toggle-navigation"
   :exec #(rf/dispatch [:toggle-navigation])
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

(def insert-time-command
  {:name "insert-time"
   :exec #(ace/insert-at-cursor @ace-editor (.format (js/moment) "YYYY-MM-DD HH:mm:ss"))
   :bindKey {:mac "cmd-q" :win "ctrl-q"}
   :readOnly false})

(defn initialize [init]
  (reset! anti-forgery-field (:anti-forgery-field init))
  (reset! ace-editor (ace/new-instance "container-ace-editor"))
  (reset! ace-editor-ro (ace/new-instance "container-ace-editor-ro"))
  (ace/set-read-only @ace-editor-ro true)
  (.on @ace-editor "change" #(if-not (.-autoChange @ace-editor) (rf/dispatch [:change %])))
  (.on @ace-editor "changeSelection"
    #(if-not (.-autoChangeSelection @ace-editor)
       (rf/dispatch [:change-selection (ace/get-selection @ace-editor)])))
  (.addCommand (.-commands @ace-editor) (clj->js (toggle-nav-command @ace-editor)))
  (.addCommand (.-commands @ace-editor) (clj->js tab-command))
  (.addCommand (.-commands @ace-editor-ro) (clj->js insert-time-command))
  (.addCommand (.-commands @ace-editor-ro) (clj->js (toggle-nav-command @ace-editor-ro)))

  (.addCommand (.-commands @ace-editor) (clj->js (ctrl-up-command @ace-editor)))
  (.addCommand (.-commands @ace-editor) (clj->js (ctrl-down-command @ace-editor)))
  (.addCommand (.-commands @ace-editor-ro) (clj->js (ctrl-up-command @ace-editor-ro)))
  (.addCommand (.-commands @ace-editor-ro) (clj->js (ctrl-down-command @ace-editor-ro))))

(defn on-blur []
  (ace/hide-clickables @ace-editor)
  (ace/hide-clickables @ace-editor-ro))

(defn on-press-key
  [{:keys [code key ctrl-key shift-key original]}]
  (when (= "Control" key)
    (ace/show-clickables @ace-editor)
    (ace/show-clickables @ace-editor-ro))
  (when (and ctrl-key (= "p" key))
    (.preventDefault original)
    (rf/dispatch [:toggle-navigation]))
  (when (and ctrl-key (= "i" key))
    (.preventDefault original)
    (.click (js/document.getElementById "file-input"))))

(defn on-release-key
  [{:keys [code key ctrl-key shift-key original]}]
  (when (= "Control" key)
    (ace/hide-clickables @ace-editor)
    (ace/hide-clickables @ace-editor-ro)))

(defn next-search [search backwards]
  (doseq [e [@ace-editor @ace-editor-ro]] (ace/navigate e search {:backwards backwards})))

(defn get-name-and-content []
  {:name @ace-editor-note-name
   :content (ace/get-text @ace-editor)})

(defn focus []
  (.focus @ace-editor))

(defn :open-history [content search]
  (ace/set-text @ace-editor-ro content)
  (when search
    (ace/navigate @ace-editor-ro search)))

(defn set-editable [can-edit?]
  (console-log "set editable" can-edit?)
  (ace/set-read-only @ace-editor (not can-edit?)))

(defn open-note ([note] (open-note note nil))
  ([{:keys [name content selection]} search]
   (console-log (str "open note " name))
   (reset! ace-editor-note-name name)
   (set! (.-autoChangeSelection @ace-editor) true)
   (ace/set-text @ace-editor (or content ""))
   (js/setTimeout
     #(do (ace/set-selection @ace-editor selection)
          (ace/navigate @ace-editor search)
          (.focus @ace-editor)
          (set! (.-autoChangeSelection @ace-editor) false)) 0)))

(defn preview-note
  [{:keys [content selection]} search]
  (ace/set-text @ace-editor-ro (or content ""))
  (js/setTimeout
    #(do (ace/set-selection @ace-editor-ro selection)
         (ace/navigate @ace-editor-ro search)) 0))

(defn open-note-after-preview [{:keys [name]}]
  (reset! ace-editor-note-name name)
  (set! (.-autoChangeSelection @ace-editor) true)
  (ace/set-text @ace-editor (ace/get-text @ace-editor-ro))
  (js/setTimeout
    #(do (ace/set-selection @ace-editor (ace/get-selection @ace-editor-ro))
         (.focus @ace-editor)
         (set! (.-autoChangeSelection @ace-editor) false)) 0))

(defn accept-external-change [{:keys [name content]}]
  (if (= name @ace-editor-note-name)
    (ace/set-text @ace-editor content)))

(defn insert-image [image-id]
  (ace/insert-at-cursor @ace-editor (str "[*" image-id "]\n")))
