(ns flo.client.ace
  (:require [clojure.set :as set]))

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
                :useSoftTabs true}))
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
      (.clearSelection (.getSelection this))
      (.scrollToLine this (inc row) true true (fn []))
      (.gotoLine this (+ row 1) col true)
      (if cursor (.moveCursorToPosition (.getSelection this) (clj->js cursor)))
      (doseq [range ranges]
        (.addRange (.getSelection this) (.clone range) true)))))

(defn navigate
  "navigates to the next occurrence of the <search> tag"
  ([this search] (navigate this search {}))
  ([this search opts]
   (if (and search (not-empty search))
     (let [settings (clj->js (set/union {"caseSensitive" true "regExp" true "backwards" false} opts))
           ; when this option is passed, will only navigate to [TAG=] not [TAG]
           regex (if (:declaration-only opts)
                   (str "\\[" search "=\\]")
                   (str "\\[" search "=?\\]"))]
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
