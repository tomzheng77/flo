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

;if (event.pageX == null && event.clientX != null) {
;    eventDoc = (event.target && event.target.ownerDocument) || document;
;    doc = eventDoc.documentElement;
;    body = eventDoc.body;
;
;    event.pageX = event.clientX +
;      (doc && doc.scrollLeft || body && body.scrollLeft || 0) -
;      (doc && doc.clientLeft || body && body.clientLeft || 0);
;    event.pageY = event.clientY +
;      (doc && doc.scrollTop  || body && body.scrollTop  || 0) -
;      (doc && doc.clientTop  || body && body.clientTop  || 0 );
;}
(defn- assign-document-scroll [event]
  (if (and (nil? (.-pageX event)) (not (nil? (.-clientX event))))
    (let [event-doc (or (and (.-target event) (.. event -target -ownerDocument)) js/document)
          doc (.-documentElement event-doc)
          body (.-body event-doc)]
      (set! (.-pageX event)
            (- (+ (.-clientX event)
                   (or (and doc (.-scrollLeft doc)) (and body (.-scrollLeft body)) 0))
               (or (and doc (.-clientLeft doc)) (and body (.-clientLeft body)) 0)))
      (set! (.-pageY event)
            (- (+ (.-clientY event)
                   (or (and doc (.-scrollTop doc)) (and body (.-scrollTop body)) 0))
               (or (and doc (.-clientTop doc)) (and body (.-clientTop body)) 0))))))

(defn- touch-0 [event]
  (and
    (.-touches event) (< 0 (.-length (.-touches event)))
    {:x (.-clientX (aget (.-touches event) 0))
     :y (.-clientY (aget (.-touches event) 0))}))

(defn to-clj-event [event]
  (assign-document-scroll event)
  {:code (. event -code)
   :key (. event -key)
   :ctrl-key (. event -ctrlKey)
   :shift-key (. event -shiftKey)
   :repeat (. event -repeat)
   :original event
   :mouse-x (or (.-pageX event) (:x (touch-0 event)))
   :mouse-y (or (.-pageY event) (:y (touch-0 event)))})

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
