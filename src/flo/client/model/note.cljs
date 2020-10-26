(ns flo.client.model.note
  (:require [clojure.data.avl :as avl]
            [flo.client.functions :refer [find-all]]
            [flo.client.model.selection :as s]))

;; creates a note object. notes are the central data structure
;; of this application
(defn new-note [name time]
  {:type         :note
   :name         name
   :time-created time

   ; time-updated is when the :content attribute
   ; was last changed for this note
   :time-updated time

   ; time-changed is when last a change was made
   ; to the editor while having this note open
   ; this can be used when collaborative editing
   :time-changed time

   ; the content of this note as a string
   ; this may be synced from the editor only when
   ; the save fx is triggered
   ; (not immediately when a change is made)
   ; :content is provided by the server initially, then synced from the editor at a fixed interval
   :content      ""

   ; a map of timestamp to content string
   ; which stores past versions of this note
   :history      (avl/sorted-map)

   ; the currently selected area of this note
   ; updated as soon as a change is made in the editor
   :selection    nil

   ; the navigation tag of this note
   ; should be a string of only alphanumeric characters
   :ntag         nil})

;; produces a suffix which represents the selected area of the note
;; or nil if a selection range does not exist
(defn note-selection-suffix [note]
  (let [selected-range (first (sort-by #(vector (:start-row %) (:start-column %)) (:ranges (:selection note))))]
    (if (not (nil? selected-range))
      (str ":" (s/range-to-str selected-range)))))

;; changes the :selection of the note to select the first
;; occurrence of text that matches the provided pattern. pattern
;; can span multiple lines.
;; if pattern does not occur in note, :selection is not changed
(defn note-select-first-occurrence [note match]
  (if-not note nil
     (let [content (or (:content note) "")
           first-match (first (find-all content match))]
       (if-not first-match note
         (let [before-start (subs content 0 (:start first-match))
               before-end (subs content 0 (:end first-match))
               start-row (count (filter #(= \newline %) before-start))
               start-column (count (take-while #(not= \newline %) (reverse before-start)))
               end-row (count (filter #(= \newline %) before-end))
               end-column (count (take-while #(not= \newline %) (reverse before-end)))
               range {:start-row start-row :start-column start-column :end-row end-row :end-column end-column}]
           (assoc note :selection (s/range-to-selection range)))))))

;; changes the selection of the note to become that specified
;; if note is nil, then nil is returned
;; if selection is nil, then the note is unchanged
(defn note-set-selection [note selection]
  (if (nil? note)
    nil
    (if (nil? selection)
      note
      (update note :selection #(or selection %)))))
