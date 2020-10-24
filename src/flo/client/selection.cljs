(ns flo.client.selection
  (:require [clojure.string :as str]
            [flo.client.constants :as c]))

;; maps the values of the map m using a function f
(defn map-values [m f]
  (into {} (for [[k v] m] [k (f v)])))

;; converts each value of a selection range object into int
;; and fills in empty fields with default values
(def infinity 2147483647)
(defn fix-range [range]
  (if (nil? range) nil
    (let [r (map-values range #(if (string? %) (js/parseInt %) %))]
      {:start-row (:start-row r)
       :start-column (or (:start-column r) 0)
       :end-row (or (:end-row r) (:start-row r))
       :end-column (or (:end-column r) infinity)})))

;; checks if the specified range is valid, that is,
;; if its start-row is non-negative and
;; its end coordinates are not before its start coordinates
(defn is-valid [range-in]
  (let [range (fix-range range-in)]
    (if (nil? range) false
      (and (>= (:start-row range) 0)
           (or (> (:end-row range) (:start-row range))
               (and (= (:end-row range) (:start-row range))
                    (>= (:end-column range) (:start-column range))))))))

(defn valid-range-to-str [range]
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
    (if (not (is-valid range)) nil
      (valid-range-to-str range))))

(defn str-to-range-internal [str]
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
;; "${start-row}-${end-row}" (start row to end row)
;; "${start-row},${start-column}-${end-row}" (start coordinate to end row)
;; "${start-row}-${end-row},${end-column}" (start row to end coordinate)
;; "${start-row},${start-column}-${end-row},${end-column}" (start coordinate to end coordinate)
(defn str-to-range [str]
  (-> (str-to-range-internal str)
      (fix-range)
      (update :start-row dec)
      (update :end-row dec)
      (#(if (is-valid %) %))))

;; produces a suffix which represents the selected area of the note
;; or nil if a selection range does not exist
(defn note-selection-suffix [note]
  (let [selected-range (first (sort-by #(vector (:start-row %) (:start-column %)) (:ranges (:selection note))))]
    (if (is-valid selected-range)
      (str ":" (range-to-str selected-range)))))

;; determines the note name and selection range from the url specified
(defn parse-url-hash [url]
  (let [hash-text (re-find c/url-hash-regex url)]
    (if hash-text
      (let [[note-name range-str] (str/split hash-text #":" 2)
            range (str-to-range range-str)]
        {:note-name note-name :range range}))))

;; converts the specified range into a possibly nil selection
(defn range-to-selection [range-in]
  (if (not (is-valid range-in)) nil
    (let [range (fix-range range-in)]
      {:cursor {:row (:start-row range) :column (:start-column range)}
       :ranges [range]})))

;; sets the note with the specified note-name inside the db
;; to have the corresponding range as its selection
;; if the note does not exist or the range is nil, then no changes are made
(defn set-note-selection [db note-name range]
  (if (or (nil? (get-in db [:notes note-name])) (nil? range)) db
     (assoc-in db [:notes note-name :selection] (range-to-selection range))))
