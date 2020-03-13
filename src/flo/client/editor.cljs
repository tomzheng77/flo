(ns flo.client.editor
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.editor-ace :as editor-ace]
    [flo.client.editor-excel :as editor-excel]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
    [flo.client.store :refer [add-watches-db add-watch-db db active-history]]
    [flo.client.network]
    [flo.client.view :refer [navigation search-bar history-bar]]
    [flo.client.constants :as c]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.data.avl :as avl]
    [cljsjs.moment]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]
    [reagent.dom :as rd]
    [clojure.set :as set]))

; the editor is a facade for
; editing, previewing and viewing the history of individual notes
; it supports both table and text mode

(def state (r/atom {
 :active-instance :ace-editor
 :open-note-name nil
 :preview-note-name nil
 :prefer-table? false
 :instances {
  ; each instance data struture should have:
  ; a :view property which contains a component to mount into reagent
  ; an :active? atom which when set will decide visibility
  :ace-editor (editor-ace/new-instance)
  :ace-editor-preview (editor-ace/new-instance {:read-only? true :init-active? false})
  :ace-editor-history (editor-ace/new-instance {:read-only? true :init-active? false})}}))

(defn view []
  (into []
    (concat
      [:div {:style {:flex-grow 1 :display "flex" :flex-direction "column"}}]
      (into [] (for [[k mode] (:instances @state)] [(mode :view)])))))

; opens the note in the appropriate instance
; sets the open note name
(defn open-note
  ([note] (open-note {}))
  ([note {:keys [search]}]))

; checks if the preview note name is
; the same as the open note name
; if so, copies state from preview instance to regular instance
(defn open-note-after-preview
  ([note] (open-note-after-preview note {}))
  ([note {:keys [search]}]))

; opens the content in the appropriate instance
; sets the active instance
(defn open-history
  ([content] (open-history content {}))
  ([content {:keys [search]}]))

; closes the history window and attempts to go back
; to the regular editor
; does nothing if history is not open
(defn close-history [])

; preview the note in the appropriate instance
; sets the preview note name
(defn preview-note
  ([note] (preview-note note {}))
  ([note {:keys [search]}]))

; passed down to the active instance
(defn goto-search [keyword reverse?])

; inserts an image with the specified id
(defn insert-image [image-id])

; passed down to the active instance
(defn focus [])

; returns the content of the active instance and the note name
; usually for saving the content
(defn get-name-and-content [])

; passed down to the active instance
; if the name of the note matches
(defn accept-external-change [note])

; sets the prefer-table attribute to true or false
; if changed from state, then switch to the corresponding editor
(defn set-prefer-table [prefer-table?])

; passed down to the active instance
(defn on-press-key [event])
(defn on-release-key [event])
(defn on-window-blur [event])
