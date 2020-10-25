(ns flo.client.model.event)

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
(defn- assign-document-scroll [dom-event]
  (if (and (nil? (.-pageX dom-event)) (not (nil? (.-clientX dom-event))))
    (let [event-doc (or (and (.-target dom-event) (.. dom-event -target -ownerDocument)) js/document)
          doc (.-documentElement event-doc)
          body (.-body event-doc)]
      (set! (.-pageX dom-event)
            (- (+ (.-clientX dom-event)
                   (or (and doc (.-scrollLeft doc)) (and body (.-scrollLeft body)) 0))
               (or (and doc (.-clientLeft doc)) (and body (.-clientLeft body)) 0)))
      (set! (.-pageY dom-event)
            (- (+ (.-clientY dom-event)
                   (or (and doc (.-scrollTop doc)) (and body (.-scrollTop body)) 0))
               (or (and doc (.-clientTop doc)) (and body (.-clientTop body)) 0))))))

(defn- touch-0 [event]
  (and
    (.-touches event) (< 0 (.-length (.-touches event)))
    {:x (.-clientX (aget (.-touches event) 0))
     :y (.-clientY (aget (.-touches event) 0))}))

(defn from-dom-event [dom-event]
  (assign-document-scroll dom-event)
  {:code (. dom-event -code)
   :key (. dom-event -key)
   :ctrl-key (. dom-event -ctrlKey)
   :shift-key (. dom-event -shiftKey)
   :repeat (. dom-event -repeat)
   :original dom-event
   :mouse-x (or (.-pageX dom-event) (:x (touch-0 dom-event)))
   :mouse-y (or (.-pageY dom-event) (:y (touch-0 dom-event)))})
