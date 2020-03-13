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
    [re-frame.core :as rf]
    [clojure.set :as set]))

; the editor is a facade for
; editing, previewing and viewing the history of individual notes
; it supports both table and text mode

(def state (r/atom nil))
(defn event-handler [instance-label event]
  (when (= instance-label (@state :active-instance))
    (rf/dispatch event)))

(reset! state {
 :active-instance :ace-editor
 :active-instance-before-history :ace-editor
 :open-note-name nil
 :preview-note-name nil

 ; state may only transition between ace editors or excel editors
 ; in all methods except change-editor
 :instances {
  ; each instance data struture should have:
  ; a :view property which contains a component to mount into reagent
  ; an :active? atom which when set will decide visibility
  :ace-editor (editor-ace/new-instance {:event-handler #(event-handler :ace-editor %)})
  :ace-editor-preview
  (editor-ace/new-instance {
   :read-only? true
   :init-active? false
   :event-handler
   #(event-handler :ace-editor-preview %)})

  :ace-editor-history
  (editor-ace/new-instance {
   :read-only? true
   :init-active? false
   :event-handler
   #(event-handler :ace-editor-history %)})

  :excel-editor (editor-excel/new-instance {:event-handler #(event-handler :excel-editor %)})
  :excel-editor-preview
  (editor-excel/new-instance {
   :read-only? true
   :init-active? false
   :event-handler
   #(event-handler :excel-editor-preview %)})

  :excel-editor-history
  (editor-excel/new-instance {
   :read-only? true
   :init-active? false
   :event-handler
   #(event-handler :excel-editor-history %)})}})

(defn active-instance []
  ((:active-instance @state) (:instances @state)))

(defn active-instance-type []
  (case (:active-instance @state)
    :excel-editor :excel
    :excel-editor-history :excel
    :excel-editor-preview :excel
    :ace-editor :ace
    :ace-editor-history :ace
    :ace-editor-preview :ace))

(defn set-instance [instance-label]  
  ; set the active instance
  (swap! state #(assoc % :active-instance instance-label))

  ; set the known last instance before activating the history instance
  (when (and (not (= instance-label :excel-editor-history))
             (not (= instance-label :ace-editor-history)))
    (swap! state #(assoc % :active-instance-before-history instance-label)))

  (reset! (:active? (instance-label (:instances @state))) true)
  (doseq [[k instance] (:instances @state)]
    (when-not (= k instance-label)
      (reset! (:active? instance) false))))

(defn get-instance [instance-label]
  (-> @state :instances instance-label))

(defn view []
  (into []
    (concat
      [:div {:style {:flex-grow 1 :display "flex" :flex-direction "column"}}]
      (into [] (for [[k instance] (:instances @state)] [(:view instance)])))))

; opens the note in the appropriate instance
; sets the open note name
(defn open-note
  ([note] (open-note note {}))
  ([note open-opts]
   (swap! state #(assoc % :open-note-name (:name note)))
   (let [use-editor (:use-editor open-opts)]
     (console-log use-editor)
     (case (or use-editor (active-instance-type))
           :excel (do (set-instance :excel-editor) (editor-excel/open-note (get-instance :excel-editor) note open-opts))
           :ace (do (set-instance :ace-editor) (editor-ace/open-note (get-instance :ace-editor) note open-opts))))))

; checks if the preview note name is
; the same as the open note name
; if so, copies state from preview instance to regular instance
(defn open-note-after-preview
  ([note] (open-note-after-preview note {}))
  ([note open-opts]
   (if-not (= (:name note) (:preview-note-name @state))
     (open-note note open-opts))
     (let [use-editor (:use-editor open-opts)
           preview-instance (active-instance)]
       (case (:active-instance @state)
         :excel-editor-preview
         (if (= use-editor :ace)
           (open-note note open-opts)
           (do (set-instance :excel-editor) (editor-excel/copy-state (get-instance :excel-editor) preview-instance)))
         :ace-editor-preview
         (if (= use-editor :excel)
           (open-note note open-opts)
           (do (set-instance :ace-editor) (editor-ace/copy-state (get-instance :ace-editor) preview-instance)))))))

; opens the content in the appropriate instance
; sets the active instance
(defn open-history
  ([content] (open-history content {}))
  ([content open-opts]
   (let [use-editor (:use-editor open-opts)]
     (case (or use-editor (active-instance-type))
       :excel (do (set-instance :excel-editor-history) (editor-excel/open-note (get-instance :excel-editor-history) {:content content} open-opts))
       :ace (do (set-instance :ace-editor-history) (editor-ace/open-note (get-instance :ace-editor-history) {:content content} open-opts))))))

; closes the history window and attempts to go back
; to the regular editor
; does nothing if history is not open
(defn close-history []
  (case (:active-instance @state)
    :excel-editor-history (set-instance (:active-instance-before-history @state))
    :ace-editor-history (set-instance (:active-instance-before-history @state)) nil)
  (case (active-instance-type)
    :excel (rf/dispatch [:set-table-on true])
    :ace (rf/dispatch [:set-table-on false])))

; preview the note in the appropriate instance
; sets the preview note name
(defn preview-note
  ([note] (preview-note note {}))
  ([note open-opts]
   (swap! state #(assoc % :preview-note-name (:name note)))
   (let [use-editor (:use-editor open-opts)]
     (case (or use-editor (active-instance-type))
       :excel (do (set-instance :excel-editor-preview) (editor-excel/open-note (get-instance :excel-editor-preview) note open-opts))
       :ace (do (set-instance :ace-editor-preview) (editor-ace/open-note (get-instance :ace-editor-preview) note open-opts))))))

; passed down to the active instance
(defn goto-search 
  ([keyword] (goto-search keyword false))
  ([keyword reverse?]
   (case (:active-instance @state)
     :excel-editor (editor-ace/goto-search (active-instance) keyword reverse?)
     :excel-editor-history (editor-ace/goto-search (active-instance) keyword reverse?)
     :excel-editor-preview (editor-ace/goto-search (active-instance) keyword reverse?)
     :ace-editor (editor-ace/goto-search (active-instance) keyword reverse?)
     :ace-editor-history (editor-ace/goto-search (active-instance) keyword reverse?)
     :ace-editor-preview (editor-ace/goto-search (active-instance) keyword reverse?))))

; inserts an image with the specified id
(defn insert-image [image-id]
  (case (:active-instance @state)
    :excel-editor (editor-ace/insert-image (active-instance) image-id)
    :ace-editor (editor-ace/insert-image (active-instance) image-id) nil))

; passed down to the active instance
(defn focus []
  (case (:active-instance @state)
    :excel-editor (editor-excel/focus (active-instance))
    :excel-editor-history (editor-excel/focus (active-instance))
    :excel-editor-preview (editor-excel/focus (active-instance))
    :ace-editor (editor-ace/focus (active-instance))
    :ace-editor-history (editor-ace/focus (active-instance))
    :ace-editor-preview (editor-ace/focus (active-instance))))

; returns the content of the active instance and the note name
; usually for saving the content
(defn get-name-and-content []
  (let [name (:open-note-name @state)]
    (when name
      (case (:active-instance @state)
        :excel-editor {:name name :content (editor-excel/get-content (active-instance))}
        :ace-editor {:name name :content (editor-ace/get-content (active-instance))} nil))))

; passed down to the active instance
; if the name of the note matches
(defn accept-external-change [note]
  (when (= (:name note) (:open-note-name @state))
    (case (:active-instance @state)
      :excel-editor (editor-ace/focus (active-instance))
      :ace-editor (editor-ace/focus (active-instance)) nil)))

; sets the use-table attribute to true or false
; if changed from state, then switch to the corresponding editor
; only this transitions between excel and ace editors
(defn change-editor [use-editor]
  (when (= use-editor :ace)
    (case (:active-instance @state)
      :excel-editor (do (editor-ace/set-content (get-instance :ace-editor) (editor-excel/get-content (active-instance))) (set-instance :ace-editor))
      :excel-editor-history (do (editor-ace/set-content (get-instance :ace-editor-history) (editor-excel/get-content (active-instance))) (set-instance :ace-editor-history))
      :excel-editor-preview (do (editor-ace/set-content (get-instance :ace-editor-preview) (editor-excel/get-content (active-instance))) (set-instance :ace-editor-preview)) nil))
  (when (= use-editor :excel)
    (case (:active-instance @state)
      :ace-editor (do (editor-excel/set-content (get-instance :excel-editor) (editor-ace/get-content (active-instance))) (set-instance :excel-editor))
      :ace-editor-history (do (editor-excel/set-content (get-instance :excel-editor-history) (editor-ace/get-content (active-instance))) (set-instance :excel-editor-history))
      :ace-editor-preview (do (editor-excel/set-content (get-instance :excel-editor-preview) (editor-ace/get-content (active-instance))) (set-instance :excel-editor-preview)) nil)))

; passed down to the active instance
(defn on-press-key [event]
  (case (:active-instance @state)
    :excel-editor (editor-excel/on-press-key (active-instance) event)
    :excel-editor-history (editor-excel/on-press-key (active-instance) event)
    :excel-editor-preview (editor-excel/on-press-key (active-instance) event)
    :ace-editor (editor-ace/on-press-key (active-instance) event)
    :ace-editor-history (editor-ace/on-press-key (active-instance) event)
    :ace-editor-preview (editor-ace/on-press-key (active-instance) event)))

(defn on-release-key [event]
  (case (:active-instance @state)
    :excel-editor (editor-excel/on-release-key (active-instance) event)
    :excel-editor-history (editor-excel/on-release-key (active-instance) event)
    :excel-editor-preview (editor-excel/on-release-key (active-instance) event)
    :ace-editor (editor-ace/on-release-key (active-instance) event)
    :ace-editor-history (editor-ace/on-release-key (active-instance) event)
    :ace-editor-preview (editor-ace/on-release-key (active-instance) event)))

(defn on-window-blur [event]
  (case (:active-instance @state)
    :excel-editor (editor-excel/on-window-blur (active-instance) event)
    :excel-editor-history (editor-excel/on-window-blur (active-instance) event)
    :excel-editor-preview (editor-excel/on-window-blur (active-instance) event)
    :ace-editor (editor-ace/on-window-blur (active-instance) event)
    :ace-editor-history (editor-ace/on-window-blur (active-instance) event)
    :ace-editor-preview (editor-ace/on-window-blur (active-instance) event)))
