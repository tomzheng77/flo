(ns flo.client.functions
  (:require [clojure.string :as str]))

(defn json->clj [x & opts]
  (apply js->clj (concat [(.parse js/JSON (.stringify js/JSON x))] opts)))

(defn splice-last [str]
  (subs str 0 (dec (count str))))

(defn current-time-millis [] (.getTime (new js/Date)))

(defn add-event-listener [type listener]
  (println "adding event listener for" type)
  (js/document.body.addEventListener type
    (fn [event]
      (let [clj-event {:code (. event -code)
                       :key (. event -key)
                       :ctrl-key (. event -ctrlKey)
                       :shift-key (. event -shiftKey)
                       :original event}]
        (listener clj-event)))))

; https://stackoverflow.com/questions/18735665/how-can-i-get-the-positions-of-regex-matches-in-clojurescript
(defn re-pos [re s]
  (let [re (js/RegExp. (.-source re) "gms")]
    (loop [res {}]
      (if-let [m (.exec re s)]
        (recur (assoc res (.-index m) (first m)))
        res))))

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

(defn intersects [occur-a occur-b]
  (let [start-a (:index occur-a)
        start-b (:index occur-b)
        end-a (+ start-a (:length occur-a))
        end-b (+ start-b (:length occur-b))]
    (not (or (<= end-b start-a)
             (<= end-a start-b)))))

(defn remove-overlaps [occurs]
  (loop [seen [] remain occurs]
    (if (empty? remain)
      seen
      (if (some #(intersects % (first remain)) seen)
        (recur seen (next remain))
        (recur (conj seen (first remain)) (next remain))))))
