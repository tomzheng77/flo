(ns flo.client.ace
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [re-frame.core :as rf]))

(defn $ [& args] (apply js/$ args))

(def instance (atom nil))
(defn new-instance [element-id]
  (js/ace.config.set "basePath" "ace")
  (let [instance (js/ace.edit element-id)]
    (.setOptions
      instance
      (clj->js {:fontFamily "Go-Mono"
                :fontSize "10pt"
                :enableFreeSpacePreviews true
                :enableBasicAutocompletion true
                :enableLiveAutocompletion false
                :enableSnippets false
                :enableLinking true
                :tabSize 4
                :useSoftTabs true
                :enableClickables true}))
    (.setTheme instance "ace/theme/monokai")
    (.setMode (.-session instance) "ace/mode/markdown")
    instance))

(defn set-text [this text]
  (.setValue (.-session this) text))

(defn get-text [this]
  (.getValue this))

(defn set-read-only [this value]
  (.setReadOnly this value))

(defn get-cursor [this]
  (let [csr (js->clj (.getCursor (.getSelection this)))]
    {:row (get csr "row") :column (get csr "column")}))

(defn insert-at-cursor [this text]
  (let [cursor (.getCursor (.getSelection this))]
    (.insert (.-session this) cursor text)))

; receives a selection configuration object which
; can later be used in set-selection
; this may contain raw JS objects that cannot be serialized into EDN
(defn get-selection [this]
  {:cursor (get-cursor this)
   :ranges (map #(.clone %) (js->clj (.getAllRanges (.getSelection this))))})

(defn set-selection [this selection]
  (if selection
    (let [ranges (:ranges selection)
          cursor (:cursor selection)
          row (:row cursor)
          col (:column cursor)]
      ; set this special flag to indicate the selection for this editor is being
      ; changed automatically and hence should not count as a user action
      (set! (.-autoChangeSelection this) true)
      (.clearSelection (.getSelection this))
      (.scrollToLine this (inc row) true true (fn []))
      (.gotoLine this (+ row 1) col true)
      (if cursor (.moveCursorToPosition (.getSelection this) (clj->js cursor)))
      (doseq [range ranges]
        (.addRange (.getSelection this) (.clone range) false))
      (set! (.-autoChangeSelection this) false))))

; [TAG-SYNTAX]
(defn navigate
  "navigates to the next occurrence of the <search> tag"
  ([this tag] (navigate this tag {}))
  ([this tag opts]
   (if (and tag (not-empty tag))
     (let [settings (clj->js (set/union {:caseSensitive true :regExp true :backwards false} opts))
           regex (str "\\[\\$?" tag "\\]")]
       (.find this regex settings)))))

; indents all the selected ranges in the editor
(defn indent-selection [this]
  (let [selection (get-selection this)
        session (.getSession this)]
    (doseq [range (:ranges selection)]
      (.indentRows
        session
        (.-row (.-start range))
        (.-row (.-end range))
        "    "))))

(defn apply-deltas [this deltas]
  (let [doc (.getDocument (.getSession this))]
    (.applyDeltas doc (clj->js deltas))))

(defn show-clickables [this]
  (let [$clickable-layer (.-clickableLayer this)]
    (if $clickable-layer
      (.show $clickable-layer))))

(defn hide-clickables [this]
  (let [$clickable-layer (.-clickableLayer this)]
    (if $clickable-layer
      (.hide $clickable-layer))))

(def clickable-css
  "
  .ace_clickables .ace_clickable_link {
    color: #589DF6;
    position: absolute;
    font-weight: bold;
    cursor: pointer;
    z-index: 10;
    pointer-events: auto;
    border-radius: 2px;
    background: rgba(0,0,0,0);
    opacity: 0;
    user-select: none;
    text-decoration: underline;
  }
  .ace_clickables .ace_clickable_link:hover {
    opacity: 1;
  }

  .ace_content .ace_clickable {
    pointer-events: auto;
  }")

(defn get-pos [$el]
  (let [rect (.getBoundingClientRect (aget $el 0))]
    {:x (.-left rect) :y (.-top rect)}))

(defn on-after-render [err renderer]
  (let [$scroller ($ (.-container renderer))
        $content ($ (.-content renderer))
        $clickables (.find $scroller ".ace_clickables")
        clickable-list (.find $content ".ace_clickable")]
    (.empty $clickables)
    (.each
      clickable-list
      (fn [_ cl]
        (let [$cl ($ cl)
              types (into #{} (map #(subs % 4) (str/split (.-className cl) #"\s+")))
              text (.text $cl)
              pos (get-pos $cl)
              $added ($ (str
                "<div class='ace_clickable_link' style='left: "
                (:x pos)
                "px; top: "
                (:y pos)
                "px'>" text "</div>"))]
          (.appendTo $added $clickables)
          (.click $added
            (fn [event]
              (.stopPropagation event)
              (.preventDefault event)
              (rf/dispatch [:click-link types text]))))))))

(defn enable-clickables [this]
  (js/console.log "Clickables: Enabled")
  (.on (.-renderer this) "afterRender" on-after-render)
  (let [$clickable-layer ($ "<div class='ace_layer ace_clickables'></div>")]
    (set! (.-clickableLayer this) $clickable-layer)
    (.appendTo $clickable-layer ($ (.-container this)))
    (hide-clickables this)))

(defn disable-clickables [this]
  (js/console.log "Clickables: Disabled")
  (.off (.-renderer this) "afterRender" on-after-render)
  (-> (.-container this)
      (js/$)
      (.find ".ace_layer.ace_clickables")
      (.remove)))

; add separate clickable layer
; similar to fs_previews
; it adds a layer of some input elements
(js/define
  "ace/ext/clickables"
  (clj->js ["ace/editor"])
  (fn [require exports module]
    (.importCssString
      (require "../lib/dom")
      clickable-css
      "ace_clickables")
    (.defineOptions
      (require "../config")
      (.. (require "ace/editor") -Editor -prototype)
      "editor"
      (clj->js
        {:enableClickables
         {:set (fn [val] (this-as this (if val (enable-clickables this) (disable-clickables this))))
          :value false}}))))

(js/window.require
  (clj->js ["ace/ext/clickables"])
  (fn []))
