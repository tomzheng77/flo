(ns flo.client.editor.editor
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.editor.editor-ace :as editor-ace]
    [flo.client.editor.editor-excel :as editor-excel]
    [flo.client.editor.editor-graph :as editor-graph]
    [clojure.string :as str]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljsjs.moment]
    [reagent.core :as r]
    [re-frame.core :as rf]))

; the editor is a facade for editing, previewing and viewing the history of individual notes
; it supports different types such as ace, excel and graph
; TODO: refactor so that the per type methods are associated with each type, rather than hardwired
; or maybe leave as is to save having to understand a new kind of object

(def state (r/atom nil))
(defn event-handler [instance-label event]
  (when (= instance-label (@state :active-instance))
    (rf/dispatch event)))

(reset! state {
 :active-instance :ace-editor
 :active-instance-prior :ace-editor ; the active instance prior to activating history or preview
 :open-note-name nil
 :preview-note-name nil

 ; state may only transition within editors of the same type
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
   #(event-handler :excel-editor-history %)})

  ; mock wiring of the graph editor
  :graph-editor (editor-graph/new-instance {:event-handler #(event-handler :graph-editor %)})
  :graph-editor-preview
  (editor-graph/new-instance {
   :read-only? true
   :init-active? false
   :event-handler
   #(event-handler :graph-editor-preview %)})

  :graph-editor-history
  (editor-graph/new-instance {
   :read-only? true
   :init-active? false
   :event-handler
   #(event-handler :graph-editor-history %)})

  }})

(defn preferred-editor-type [content]
  (if-not content :ace
    (let [s content]
      (if (or (str/starts-with? s "\"<TBL>") (str/starts-with? s "<TBL>"))
       :excel
       (if (str/starts-with? s "{")
         :graph :ace)))))

(defn active-instance []
  ((:active-instance @state) (:instances @state)))

(defn instance-type [instance-label]
  (case instance-label
    :excel-editor         :excel
    :excel-editor-history :excel
    :excel-editor-preview :excel
    :ace-editor           :ace
    :ace-editor-history   :ace
    :ace-editor-preview   :ace
    :graph-editor         :graph
    :graph-editor-history :graph
    :graph-editor-preview :graph))

(defn instance-mode [instance-label]
  (case instance-label
    :excel-editor         :edit
    :excel-editor-history :history
    :excel-editor-preview :preview
    :ace-editor           :edit
    :ace-editor-history   :history
    :ace-editor-preview   :preview
    :graph-editor         :edit
    :graph-editor-history :history
    :graph-editor-preview :preview))

(defn active-instance-type []
  (instance-type (:active-instance @state)))

(defn active-instance-mode []
  (instance-mode (:active-instance @state)))

(defn set-instance [instance-label]  
  ; set the active instance
  (swap! state #(assoc % :active-instance instance-label))

  ; set the known last instance before activating a preview or history instance
  (when (and (not (= instance-label :excel-editor-preview))
             (not (= instance-label :excel-editor-history))
             (not (= instance-label :ace-editor-preview))
             (not (= instance-label :ace-editor-history))
             (not (= instance-label :graph-editor-preview))
             (not (= instance-label :graph-editor-history)))
    (swap! state #(assoc % :active-instance-prior instance-label)))

  (reset! (:active? (instance-label (:instances @state))) true)
  (doseq [[k instance] (:instances @state)]
    (when-not (= k instance-label)
      (reset! (:active? instance) false)))

  (rf/dispatch [:set-editor-type (instance-type instance-label) false]))

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
           :ace   (do (editor-ace/open-note   (get-instance :ace-editor)   note (assoc open-opts :focus? true)) (set-instance :ace-editor))
           :graph (do (editor-graph/open-note (get-instance :graph-editor) note (assoc open-opts :focus? true)) (set-instance :graph-editor))))))

; opens the content in the appropriate instance
; sets the active instance
(defn open-history
  ([name content] (open-history name content {}))
  ([name content open-opts]
   (let [use-editor (:use-editor open-opts)]
     (case (or use-editor (active-instance-type))
       :excel (do (editor-excel/open-note (get-instance :excel-editor-history) {:name name :content content} open-opts)
                  (set-instance :excel-editor-history))
       :ace   (do (editor-ace/open-note   (get-instance :ace-editor-history)   {:name name :content content} open-opts)
                  (set-instance :ace-editor-history))
       :graph (do (editor-graph/open-note (get-instance :graph-editor-history) {:name name :content content} open-opts)
                  (set-instance :graph-editor-history))))))

; closes the preview window and attempts to go back
; to the regular editor
; does nothing if preview is not open
(defn close-preview []
  (case (:active-instance @state)
    :excel-editor-preview (set-instance (:active-instance-prior @state))
    :ace-editor-preview   (set-instance (:active-instance-prior @state))
    :graph-editor-preview (set-instance (:active-instance-prior @state)) nil))

; closes the history window and attempts to go back
; to the regular editor
; does nothing if history is not open
(defn close-history []
  (case (:active-instance @state)
    :excel-editor-history (set-instance (:active-instance-prior @state))
    :ace-editor-history   (set-instance (:active-instance-prior @state))
    :graph-editor-history (set-instance (:active-instance-prior @state)) nil))

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
       :ace   (do (editor-ace/open-note   (get-instance :ace-editor-preview)   note open-opts)
                  (set-instance :ace-editor-preview))
       :graph (do (editor-graph/open-note (get-instance :graph-editor-preview) note open-opts)
                  (set-instance :graph-editor-preview))))))

; inserts an image with the specified id
; TODO: remove the deprecated method of inserting images altogether
(defn insert-image [image-id]
  (case (:active-instance @state)
    :excel-editor (editor-excel/insert-image (active-instance) image-id)
    :ace-editor   (editor-ace/insert-image   (active-instance) image-id) 
    :graph-editor (editor-graph/insert-image (active-instance) image-id) nil))

; passed down to the active instance
(defn focus []
  (case (:active-instance @state)
    :excel-editor         (editor-excel/focus (active-instance))
    :excel-editor-history (editor-excel/focus (active-instance))
    :excel-editor-preview (editor-excel/focus (active-instance))
    :ace-editor           (editor-ace/focus   (active-instance))
    :ace-editor-history   (editor-ace/focus   (active-instance))
    :ace-editor-preview   (editor-ace/focus   (active-instance))
    :graph-editor         (editor-graph/focus (active-instance))
    :graph-editor-history (editor-graph/focus (active-instance))
    :graph-editor-preview (editor-graph/focus (active-instance))))

; returns the content of the active instance and the note name
; usually for saving the content
(defn get-name-and-content []
  (let [name (:open-note-name @state)]
    (when name
      (case (:active-instance @state)
        :excel-editor {:name name :content (editor-excel/get-content (active-instance))}
        :ace-editor   {:name name :content (editor-ace/get-content   (active-instance))}
        ; TODO: add field for attachments (blobs to upload?)
        ; or alternatively, add a blobstore module
        :graph-editor {:name name :content (editor-graph/get-content (active-instance))} nil))))

; passed down to the active instance
; if the name of the note matches
(defn accept-external-change [note]
  (when (= (:name note) (:open-note-name @state))
    (case (:active-instance @state)
      :excel-editor (editor-excel/set-content (active-instance) (:content note))
      :ace-editor (editor-ace/set-content (active-instance) (:content note))
      :graph-editor (editor-graph/set-content (active-instance) (:content note)))))

(defn active-instance-content []
  (case (active-instance-type)
    :excel (editor-excel/get-content (active-instance))
    :ace   (editor-ace/get-content   (active-instance))
    :graph (editor-graph/get-content (active-instance))))

; sets the use-table attribute to true or false
; if changed from state, then switch to the corresponding editor
; only this transitions between excel and ace editors
(defn change-editor [editor-type]
  (if-not (= editor-type (active-instance-type))
    (let [content (active-instance-content)]
      (case [editor-type (active-instance-mode)]
        [:excel :edit   ] (do (editor-excel/set-content (get-instance :excel-editor)         content) (set-instance :excel-editor))
        [:excel :history] (do (editor-excel/set-content (get-instance :excel-editor-history) content) (set-instance :excel-editor-history))
        [:excel :preview] (do (editor-excel/set-content (get-instance :excel-editor-preview) content) (set-instance :excel-editor-preview))
        [:ace   :edit   ] (do (editor-ace/set-content   (get-instance :ace-editor)           content) (set-instance :ace-editor))
        [:ace   :history] (do (editor-ace/set-content   (get-instance :ace-editor-history)   content) (set-instance :ace-editor-history))
        [:ace   :preview] (do (editor-ace/set-content   (get-instance :ace-editor-preview)   content) (set-instance :ace-editor-preview))
        [:graph :edit   ] (do (editor-graph/set-content (get-instance :graph-editor)         content) (set-instance :graph-editor))
        [:graph :history] (do (editor-graph/set-content (get-instance :graph-editor-history) content) (set-instance :graph-editor-history))
        [:graph :preview] (do (editor-graph/set-content (get-instance :graph-editor-preview) content) (set-instance :graph-editor-preview))))))

; passed down to the active instance
(defn on-press-key [event]
  (case (:active-instance @state)
    :excel-editor         (editor-excel/on-press-key (active-instance) event)
    :excel-editor-history (editor-excel/on-press-key (active-instance) event)
    :excel-editor-preview (editor-excel/on-press-key (active-instance) event)
    :ace-editor           (editor-ace/on-press-key   (active-instance) event)
    :ace-editor-history   (editor-ace/on-press-key   (active-instance) event)
    :ace-editor-preview   (editor-ace/on-press-key   (active-instance) event)
    :graph-editor         (editor-graph/on-press-key (active-instance) event)
    :graph-editor-history (editor-graph/on-press-key (active-instance) event)
    :graph-editor-preview (editor-graph/on-press-key (active-instance) event)))

(defn on-release-key [event]
  (case (:active-instance @state)
    :excel-editor         (editor-excel/on-release-key (active-instance) event)
    :excel-editor-history (editor-excel/on-release-key (active-instance) event)
    :excel-editor-preview (editor-excel/on-release-key (active-instance) event)
    :ace-editor           (editor-ace/on-release-key   (active-instance) event)
    :ace-editor-history   (editor-ace/on-release-key   (active-instance) event)
    :ace-editor-preview   (editor-ace/on-release-key   (active-instance) event)
    :graph-editor         (editor-graph/on-release-key (active-instance) event)
    :graph-editor-history (editor-graph/on-release-key (active-instance) event)
    :graph-editor-preview (editor-graph/on-release-key (active-instance) event)))

(defn on-window-blur [event]
  (case (:active-instance @state)
    :excel-editor         (editor-excel/on-window-blur (active-instance) event)
    :excel-editor-history (editor-excel/on-window-blur (active-instance) event)
    :excel-editor-preview (editor-excel/on-window-blur (active-instance) event)
    :ace-editor           (editor-ace/on-window-blur   (active-instance) event)
    :ace-editor-history   (editor-ace/on-window-blur   (active-instance) event)
    :ace-editor-preview   (editor-ace/on-window-blur   (active-instance) event)
    :graph-editor         (editor-graph/on-window-blur (active-instance) event)
    :graph-editor-history (editor-graph/on-window-blur (active-instance) event)
    :graph-editor-preview (editor-graph/on-window-blur (active-instance) event)))
