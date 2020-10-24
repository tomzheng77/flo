(ns flo.client.ace.ace
  (:require [clojure.set :as set]
            [flo.client.selection :as s]))

;; helper function for using jQuery
(defn $ [& args] (apply js/$ args))

;; creates a new ace.Editor instance
;; which uses the element specified as its container
(defn new-instance [element-or-id]
  (js/ace.config.set "basePath" "ace")
  (let [instance (js/ace.edit element-or-id)]
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
                :enableClickables true
                :enableColors true
                :scrollPastEnd 1}))
    (.setTheme instance "ace/theme/monokai")
    (.setMode (.-session instance) "ace/mode/markdown")
    instance))

(defn set-text [this text]
  (set! (.-autoChange this) true)
  (.setValue (.-session this) text)
  (set! (.-autoChange this) false))

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

;; converts a cljs map into an ace.Range
(defn map-to-range [m]
  (let [{:keys [start-row start-column end-row end-column]} (s/fix-range m)]
    (new js/ace.Range start-row start-column end-row end-column)))

;; converts an ace.Range into a cljs map
(defn range-to-map [r edit-session]
  {:start-row (.. r -start -row)
   :start-column (.. r -start -column)
   :end-row (.. r -end -row)
   :end-column
   (let [cval (.. r -end -column)]
     (if (= cval (count (.getLine edit-session (.. r -end -row))))
       s/infinity cval))})

;; receives a selection configuration object which
;; can later be used in set-selection
(defn get-selection [this]
  {:cursor (get-cursor this)
   :ranges (vec (map #(range-to-map % (.getSession this)) (js->clj (.getAllRanges (.getSelection this)))))})

;; uses the selection configuration object to both
;; set the regions selected, and also scroll to the cursor
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
        (.addRange (.getSelection this) (map-to-range range) false))
      (set! (.-autoChangeSelection this) false))))

; [TAG-SYNTAX]
(defn navigate
  "navigates to the next occurrence of the <search> tag"
  ([this tag] (navigate this tag {}))
  ([this tag opts]
   (if (and tag (not-empty tag))
     (let [settings (clj->js (set/union {:caseSensitive true :regExp true :backwards false :skipCurrent true} opts))
           regex (str "\\[" tag "\\]")]
       (.find this regex settings)))))

; indents all the selected ranges in the editor
(defn indent-selection [this]
  (let [selection (get-selection this)
        session (.getSession this)]
    (doseq [range (:ranges selection)]
      (.indentRows
        session
        (:start-row range)
        (:end-row range)
        "    "))))

(defn show-clickables [this]
  (let [$clickable-layer (.-clickableLayer this)]
    (if $clickable-layer
      (.show $clickable-layer))))

(defn hide-clickables [this]
  (let [$clickable-layer (.-clickableLayer this)]
    (if $clickable-layer
      (.hide $clickable-layer))))
