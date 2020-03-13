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

; open-note [note opts]
; open-note-after-preview [note opts]
; open-history [content opts]
; preview-note [note opts]

; set-editable [editable?]
; next-search [keyword reverse?]
; accept-external-change [note]
; insert-image [image-id]
; focus []
; get-name-and-content []

; on-press-key [event]
; on-release-key [event]
; on-window-blur [event]

(def state (r/atom {
 :active-instance :ace-editor
 :open-note-name nil
 :preview-note-name nil
 :instances {
  ; each instance data struture should have:
  ; a :view property which contains a component to mount into reagent
  ; an :active? atom which when set will decide visibility
  :ace-editor (editor-ace/new-instance)
  :ace-editor-preview (editor-ace/new-instance {:read-only? true :init-active? false})
  :ace-editor-history (editor-ace/new-instance {:read-only? true :init-active? false})}}))

(console-log (clj->js (:instances @state)))

(defn view []
  (concat
    [:div {:style {:flex-grow 1 :display "flex" :flex-direction "column"}}]
    (into [] (for [[k mode] (:instances @state)] [(mode :view)]))))

(defn open-note [note {:keys [prefer-table? search]}])
(defn open-note-after-preview
  ([note] (open-note-after-preview note {}))
  ([note {:keys [prefer-table? search]}]))
(defn open-history [entry {:keys [prefer-table? search]}])
(defn preview-note [note {:keys [prefer-table? search]}])

(defn set-editable [editable?])
(defn next-search [keyword reverse?])
(defn accept-external-change [note])
(defn insert-image [image-id])
(defn focus [])
(defn get-name-and-content [])

(defn on-press-key [event])
(defn on-release-key [event])
(defn on-window-blur [event])
