(ns flo.client.model.selection
  (:require [clojure.string :as str]
            [flo.client.constants :as c]))

;;;; quiz: what are the selection, cursor and range objects?
;;;; quiz: when is each kind of object invalid?

;;;; this file defines the semantics of the selection and the range data structures
;;;; the selection object is designed to accurately represent all the regions which the
;;;; users have selected in a plaintext editor
;;;; its only shortcoming is the inability to address, when having multiple selection
;;;; regions, whether the cursor of each should be at the start or at the end

;;;; a selection object is a tuple of cursor and range
;;;; the cursor is a row-col coordinate
;;;; the range is a pair of row-col coordinates for start and end
(comment {:cursor {:row 3 :column 2} :ranges [{:start-row 1 :start-column 3 :end-row 3 :end-column 5}]})

;;;; example of a cursor at line 4, column 2
;;;; note that rows are stored as 0-indexed, but should be
;;;; interpreted as 1-indexed
;;;; row and column numbers must be non-negative integers
(comment {:row 3 :column 2})

;;;; in relation to the text editor,
;;;; a row value that is out of bounds should put the cursor after the last character of the document
;;;; a column value that is outside the line should put the cursor at the end of that line

;;;; example of a range from row 2, column 3 to row 4, column 5
;;;; the end coordinate must occur after the start coordinate
;;;; that is, either the end row is after the start row, or the
;;;; start and end rows are the same and the end column is after the start column
(comment {:start-row 1 :start-column 3 :end-row 3 :end-column 5})

;; maps the values of the map m using a function f
(defn- map-values [m f]
  (into {} (for [[k v] m] [k (f v)])))

;; default selection
(def default {:cursor {:row 0 :column 0}})

;; converts each value of a selection range object into int
;; and fills in empty fields with default values
(def infinity 2147483647)
(defn- fix-range [range]
  (if (nil? range) nil
    (let [r (map-values range #(if (string? %) (js/parseInt %) %))]
      {:start-row (:start-row r)
       :start-column (or (:start-column r) 0)
       :end-row (or (:end-row r) (:start-row r))
       :end-column (or (:end-column r) infinity)})))

;; checks if the specified range is valid, that is,
;; if its start-row is non-negative and
;; its end coordinates are not before its start coordinates
;; there is no need for this function to be called outside of this file
;; since all non-nil ranges should be valid
(defn- is-valid-range [range-in]
  (let [range (fix-range range-in)]
    (if (nil? range) false
      (and (>= (:start-row range) 0) (>= (:start-column range) 0)
           (or (> (:end-row range) (:start-row range))
               (and (= (:end-row range) (:start-row range))
                    (>= (:end-column range) (:start-column range))))))))

(defn- valid-range-to-str [range]
  (let [range-inc (-> range (update :start-row inc) (update :end-row inc))]
    (cond (and (= (:start-column range-inc) 0)
               (= (:end-row range-inc) (:start-row range-inc))
               (= (:end-column range-inc) infinity))
          (str (:start-row range-inc))
          (and (= (:end-row range-inc) (:start-row range-inc))
               (= (:end-column range-inc) (:start-column range-inc)))
          (str (:start-row range-inc) "," (:start-column range-inc))
          (and (= (:start-column range-inc) 0)
               (= (:end-column range-inc) infinity))
          (str (:start-row range-inc) "-" (:end-row range-inc))
          (= (:start-column range-inc) 0)
          (str (:start-row range-inc) "-" (:end-row range-inc) "," (:end-column range-inc))
          (= (:end-column range-inc) infinity)
          (str (:start-row range-inc) "," (:start-column range-inc) "-" (:end-row range-inc))
          :else
          (str (:start-row range-inc) ","
               (:start-column range-inc) "-"
               (:end-row range-inc) ","
               (:end-column range-inc)))))

;; converts a selection range into an accurate
;; coordinate-to-coordinate string format
;; i.e. "${start-row},${start-column}-${end-row},${end-column}"
(defn range-to-str [range-in]
  (let [range (fix-range range-in)]
    (if (not (is-valid-range range)) nil
       (valid-range-to-str range))))

(defn- str-to-range-internal [str]
  (cond (nil? str) nil
    (re-matches #"[0-9]+" str)
    (let [a (js/parseInt str)] {:start-row a})

    (re-matches #"[0-9]+,[0-9]+" str)
    (let [[a b] (str/split str #",")]
      {:start-row a :start-column b :end-row a :end-column b})

    (re-matches #"[0-9]+-[0-9]+" str)
    (let [[a b] (str/split str #"-")]
      {:start-row a :end-row b})

    (re-matches #"[0-9]+,[0-9]+-[0-9]+" str)
    (let [[a b c] (str/split str #"[,-]")]
      {:start-row a :start-column b :end-row c})

    (re-matches #"[0-9]+-[0-9]+,[0-9]+" str)
    (let [[a b c] (str/split str #"[,-]")]
      {:start-row a :end-row b :end-column c})

    (re-matches #"[0-9]+,[0-9]+-[0-9]+,[0-9]+" str)
    (let [[a b c d] (str/split str #"[,-]")]
      {:start-row a
       :start-column b
       :end-row c
       :end-column d})))

;; converts from five different types of string representations
;; into a selection range, the formats are:
;; "${start-and-end-row}" (single row)
;; "${cursor-row}-${cursor-column}" (equal start and end coordinates)
;; "${start-row},${start column}" (end coordinate equal to start coordinate)
;; "${start-row}-${end-row}" (start row to end row)
;; "${start-row},${start-column}-${end-row}" (start coordinate to end row)
;; "${start-row}-${end-row},${end-column}" (start row to end coordinate)
;; "${start-row},${start-column}-${end-row},${end-column}" (start coordinate to end coordinate)
(defn str-to-range [str]
  (-> (str-to-range-internal str)
      (fix-range)
      (update :start-row dec)
      (update :end-row dec)
      (#(if (is-valid-range %) %))))

;; converts the specified range into a possibly nil selection
(defn range-to-selection [range-in]
  (if (not (is-valid-range range-in)) nil
    (let [range (fix-range range-in)]
      {:cursor {:row (:start-row range) :column (:start-column range)}
       :ranges [range]})))

;; sets the note with the specified note-name inside the db
;; to have the corresponding range as its selection
;; if the note does not exist or the range is nil, then no changes are made
(defn set-note-selection [db note-name range]
  (if (or (nil? (get-in db [:notes note-name])) (nil? range)) db
     (assoc-in db [:notes note-name :selection] (range-to-selection range))))
