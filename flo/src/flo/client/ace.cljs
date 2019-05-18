(ns flo.client.ace
  (:require [clojure.set :as set]))

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

(defn navigate
  "navigates to the next occurrence of the <search> tag"
  ([this tag] (navigate this tag {}))
  ([this tag opts]
   (if (and tag (not-empty tag))
     (let [settings (clj->js (set/union {:caseSensitive true :regExp true :backwards false} opts))
           regex (str "\\[" tag "=?\\]")]
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

(def clickable-css
  "
  .ace_clickables .ace_clickable_link {
    position: absolute;
    font-weight: bold;
    cursor: pointer;
    z-index: 10;
    pointer-events: auto;
    background-color: red;
    border-radius: 2px;
  }
  .ace_content .ace_clickable {
    pointer-events: auto;
  }")

(defn get-pos
  ([$el] (get-pos $el 0))
  ([$el n-parents]
   (if (>= 0 n-parents)
    (let [this-pos (js->clj (.position $el))]
      {:x (or (get this-pos "left") 0)
       :y (or (get this-pos "top") 0)})
    (let [parent-pos (get-pos (.parent $el) (dec n-parents))
          this-pos (get-pos $el)]
      {:x (+ (:x parent-pos) (:x this-pos))
       :y (+ (:y parent-pos) (:y this-pos))}))))

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
              text (.text $cl)
              pos (get-pos $cl 4)
              $added ($ (str
                "<div class='ace_clickable_link' style='left: "
                (+ (:x pos) 4)
                "px; top: "
                (:y pos)
                "px'>" text "</div>"))]
          (.appendTo $added $clickables)
          (.click $added
            (fn [event]
              (.stopPropagation event)
              (.preventDefault event)
              ))
          )))))

(defn enable-clickables [this]
  (js/console.log "Clickables: Enabled")
  (.on (.-renderer this) "afterRender" on-after-render)
  (let [$clickable-layer ($ "<div class='ace_layer ace_clickables'></div>")]
    (.appendTo $clickable-layer ($ (.-container this)))))

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
