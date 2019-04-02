(ns flo.functions)

(defn json->clj [x & opts]
  (apply js->clj (concat [(.parse js/JSON (.stringify js/JSON x))] opts)))

(defn splice-last [str]
  (subs str 0 (dec (count str))))

(defn current-time-millis [] (.getTime (new js/Date)))

(defn add-event-listener [type listener]
  (js/addEventListener type
    (fn [event]
      (let [clj-event {:code (. event -code) :key (. event -key)}]
        (listener clj-event)))))
