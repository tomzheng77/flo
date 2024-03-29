(ns flo.client.functions
  (:require [clojure.string :as str]))

(defn clamp [min max x]
  (if (< x min)
    min
    (if (> x max) max x)))

(defn json->clj [x & opts]
  (apply js->clj (concat [(.parse js/JSON (.stringify js/JSON x))] opts)))

(defn splice-last [str]
  (subs str 0 (dec (count str))))

(defn current-time-millis [] (.getTime (new js/Date)))

; https://stackoverflow.com/questions/18735665/how-can-i-get-the-positions-of-regex-matches-in-clojurescript
; matches a regex pattern to the string provided and
; produces a list of pairs, each contains the starting index and the matched substring
(defn- re-pos [re s]
  (let [re (js/RegExp. (.-source re) "gms")]
    (loop [res {}]
      (if-let [m (.exec re s)]
        (recur (assoc res (.-index m) (first m))) res))))

;; matches a pattern against the text
;; and returns a list of comprehensive match objects
;; representing all of the occurrences inside text
;;
;; each object contains:
;; :index, the position of the start of the match
;; :length, the length of the match within the text
;; :start, same as :index
;; :end, equal to :index + :length
;; :substr, text[:start..:end]
(defn find-all [text match]
  (cond
    (string? match)
    (loop [start-index 0 output []]
      (let [index (str/index-of text match start-index)]
        (if-not index
          output
          (recur
            (inc start-index)
            (conj output
                  {:index index :length (count match)
                   :start index :end (+ index (count match))
                   :substr match})))))
    (regexp? match)
    (for [[index substr] (re-pos match text)]
      {:index index :length (count substr)
       :start index :end (+ index (count substr))
       :substr substr})))
