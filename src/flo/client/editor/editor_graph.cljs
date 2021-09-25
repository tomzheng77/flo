(ns flo.client.editor.editor-graph)

; ========== [PUBLIC METHODS] ==========

(defn new-instance
  ([] (new-instance {}))
  ([{:keys [read-only? event-handler init-active?]}]
   (let [source (r/atom []) active? (r/atom (if (nil? init-active?) true init-active?))]
     {:instance-type :client-graph-editor
      :source source
      :view (fn []
        (r/create-class {
          :reagent-render #(view-render source active?)}))
      :active? active?
      :event-handler event-handler})))

(defn get-content [this] "")
(defn set-content [this content])
(defn open-note
  ([this note] (open-note this note nil))
  ([this {:keys [content]} open-opts] (set-content this content)))

(defn focus [this])
(defn on-press-key [this event])
(defn on-release-key [this event])
(defn on-window-blur [this event])

; DEPRECATED, since it is preferred that images are stored as blobs
(defn insert-image [this image-id])

