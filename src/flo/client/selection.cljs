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

;; converts a selection range into an accurate
;; coordinate-to-coordinate string format
;; i.e. "${start-row},${start-column}-${end-row},${end-column}"
(defn range-to-str [range-in]
  (let [range
        (-> (fix-range range-in)
            (update :start-row inc)
            (update :end-row inc))]
    (cond (nil? range) nil
      (and (= (:start-column range) 0)
           (= (:end-row range) (:start-row range))
           (= (:end-column range) infinity))
      (str (:start-row range))
      (and (= (:start-column range) 0)
           (= (:end-column range) infinity))
      (str (:start-row range) "-" (:end-row range))
      (= (:start-column range) 0)
      (str (:start-row range) "-" (:end-row range) "," (:end-column range))
      (= (:end-column range) infinity)
      (str (:start-row range) "," (:start-column range) "-" (:end-row range))
      :else
      (str (:start-row range) ","
           (:start-column range) "-"
           (:end-row range) ","
           (:end-column range)))))

(defn str-to-range-internal [str]
  (cond (nil? str) nil
    (re-matches #"[0-9]+" str)
    (let [a (js/parseInt str)] {:start-row a})

    (re-matches #"[0-9]+-[0-9]+" str)
    (let [[a b] (str/split str #"-")] {:start-row a :end-row b})

    (re-matches #"[0-9]+,[0-9]+-[0-9]+" str)
    (let [[a b c] (str/split str #"[,-]")] {:start-row a :start-column b :end-row c})

    (re-matches #"[0-9]+-[0-9]+,[0-9]+" str)
    (let [[a b c] (str/split str #"[,-]")] {:start-row a :end-row b :end-column c})

    (re-matches #"[0-9]+,[0-9]+-[0-9]+,[0-9]+" str)
    (let [[a b c d] (str/split str #"[,-]")]
      {:start-row a
       :start-column b
       :end-row c
       :end-column d})))

;; converts from five different types of string representations
;; into a selection range, the formats are:
;; "${start-and-end-row}" (single row)
;; "${start-row}-${end-row}" (start row to end row)
;; "${start-row},${start-column}-${end-row}" (start coordinate to end row)
;; "${start-row}-${end-row},${end-column}" (start row to end coordinate)
;; "${start-row},${start-column}-${end-row},${end-column}" (start coordinate to end coordinate)
(defn str-to-range [str]
  (-> (str-to-range-internal str)
      (fix-range)
      (update :start-row dec)
      (update :end-row dec)))

;; produces a suffix which represents the selected area of the note
;; or nil if a selection range does not exist
(defn note-selection-suffix [note]
  (let [selected-range (first (:ranges (:selection note)))]
    (if selected-range (str ":" (range-to-str selected-range)))))

(defn parse-url-hash [url]
  (let [hash-text (re-find c/url-hash-regex url)]
    (if hash-text
      (let [[note-name range-str] (str/split hash-text #":" 2)
            range (str-to-range range-str)]
        {:note-name note-name :range range}))))

(defn set-note-selection [db note-name range]
  (if-not range db
    (-> db
        (assoc-in [:notes note-name :selection :cursor] {:row (:start-row range) :column (:start-column range)})
        (assoc-in [:notes note-name :selection :ranges] [range]))))
