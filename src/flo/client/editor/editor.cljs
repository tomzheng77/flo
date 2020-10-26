(ns flo.client.editor.editor
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.editor.editor-ace :as editor-ace]
    [flo.client.editor.editor-excel :as editor-excel]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljsjs.moment]
    [reagent.core :as r]
    [re-frame.core :as rf]))

; the editor is a facade for
; editing, previewing and viewing the history of individual notes
; it supports both table and text mode

(def state (r/atom nil))
(defn event-handler [instance-label event]
  (when (= instance-label (@state :active-instance))
    (rf/dispatch event)))

(reset! state {
 :active-instance :ace-editor
 :active-instance-prior :ace-editor ; the active instance prior to activating history or preview
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

(defn instance-type [instance-label]
  (case instance-label
    :excel-editor :excel
    :excel-editor-history :excel
    :excel-editor-preview :excel
    :ace-editor :ace
    :ace-editor-history :ace
    :ace-editor-preview :ace))

(defn set-instance [instance-label]  
  ; set the active instance
  (swap! state #(assoc % :active-instance instance-label))

  ; set the known last instance before activating a preview or history instance
  (when (and (not (= instance-label :excel-editor-preview))
             (not (= instance-label :excel-editor-history))
             (not (= instance-label :ace-editor-preview))
             (not (= instance-label :ace-editor-history)))
    (swap! state #(assoc % :active-instance-prior instance-label)))

  (reset! (:active? (instance-label (:instances @state))) true)
  (doseq [[k instance] (:instances @state)]
    (when-not (= k instance-label)
      (reset! (:active? instance) false)))

  (case (instance-type instance-label)
    :excel (rf/dispatch [:set-table-on true false])
    :ace (rf/dispatch [:set-table-on false false])))

(defn get-instance [instance-label]
  (-> @state :instances instance-label))

(defn get-active-ace []
  (if (= :ace-editor (:active-instance @state))
    @(:ace-editor (active-instance))))

(defn view []
  (into []
    (concat
      [:div {:style {:flex-grow 1 :flex-shrink 1 :height 100 :display "flex" :flex-direction "column"}}]
      (into [] (for [[k instance] (:instances @state)] [(:view instance)])))))

; opens the note in the appropriate instance
; sets the open note name
(defn open-note
  ([note] (open-note note {}))
  ([note open-opts]
   (swap! state #(assoc % :open-note-name (:name note)))
   (let [use-editor (:use-editor open-opts)]
     (case (or use-editor (active-instance-type))
           :excel (do (editor-excel/open-note (get-instance :excel-editor) note (assoc open-opts :focus? true)) (set-instance :excel-editor))
           :ace (do (editor-ace/open-note (get-instance :ace-editor) note (assoc open-opts :focus? true)) (set-instance :ace-editor))))))

; opens the content in the appropriate instance
; sets the active instance
(defn open-history
  ([name content] (open-history name content {}))
  ([name content open-opts]
   (let [use-editor (:use-editor open-opts)]
     (case (or use-editor (active-instance-type))
       :excel (do (editor-excel/open-note (get-instance :excel-editor-history) {:name name :content content} open-opts)
                  (set-instance :excel-editor-history))
       :ace (do (editor-ace/open-note (get-instance :ace-editor-history) {:name name :content content} open-opts)
                (set-instance :ace-editor-history))))))

; closes the preview window and attempts to go back
; to the regular editor
; does nothing if preview is not open
(defn close-preview []
  (case (:active-instance @state)
    :excel-editor-preview (set-instance (:active-instance-prior @state))
    :ace-editor-preview (set-instance (:active-instance-prior @state)) nil))

; closes the history window and attempts to go back
; to the regular editor
; does nothing if history is not open
(defn close-history []
  (case (:active-instance @state)
    :excel-editor-history (set-instance (:active-instance-prior @state))
    :ace-editor-history (set-instance (:active-instance-prior @state)) nil))

; preview the note in the appropriate instance
; sets the preview note name
(defn preview-note
  ([note] (preview-note note {}))
  ([note open-opts]
   (swap! state #(assoc % :preview-note-name (:name note)))
   (let [use-editor (:use-editor open-opts)]
     (case (or use-editor (active-instance-type))
       :excel (do (editor-excel/open-note (get-instance :excel-editor-preview) note open-opts)
                  (set-instance :excel-editor-preview))
       :ace (do (editor-ace/open-note (get-instance :ace-editor-preview) note open-opts)
                (set-instance :ace-editor-preview))))))

; delegate goto-search to the active instance
(defn goto-search 
  ([keyword] (goto-search keyword false))
  ([keyword reverse?]
   (case (:active-instance @state)
     :excel-editor (editor-excel/goto-search (active-instance) keyword reverse?)
     :excel-editor-history (editor-excel/goto-search (active-instance) keyword reverse?)
     :excel-editor-preview (editor-excel/goto-search (active-instance) keyword reverse?)
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
      :excel-editor (editor-excel/set-content (active-instance) (:content note))
      :ace-editor (editor-ace/set-content (active-instance) (:content note)))))

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
