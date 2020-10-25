(ns flo.client.store.history
  (:require [re-frame.core :as rf]
            [flo.client.functions :refer [clamp]]
            [flo.client.store.watch :as w]))

(defn active-history [db] (get-in db [:notes (:active-note-name db) :history]))
(defn active-time-updated [db] (get-in db [:notes (:active-note-name db) :time-updated]))
(defn active-time-created [db] (get-in db [:notes (:active-note-name db) :time-created]))
(defn active-time-history-start [db]
  (let [updated (active-time-updated db)]
    (clamp (- updated (:history-limit db)) updated (active-time-created db))))

(rf/reg-sub :history-cursor (fn [db v] (:history-cursor db)))
(rf/reg-sub :history-limit (fn [db v] (:history-limit db)))
(rf/reg-sub :drag-btn-width (fn [db v] (:drag-btn-width db)))
(rf/reg-sub :drag-start (fn [db v] (:drag-start db)))

(rf/reg-event-db :start-drag (fn [db [_ item]] (assoc db :drag-start item)))

; event handler called whenever the user has moved the mouse
(rf/reg-event-db :mouse-move
  (fn [db [_ event]]
    (let [drag-start (:drag-start db)]
      (if-not drag-start db
        (let [dx (- (:mouse-x event) (:mouse-x drag-start))
              start-position (:btn-x drag-start)
              width (:window-width db)
              drag-position (min (max 0 (+ dx start-position)) (- width 80))
              max-drag-position (- (:window-width db) (:drag-btn-width db))
              new-history-cursor (+ (active-time-history-start db)
                                    (/ (* (- (active-time-updated db) (active-time-history-start db)) drag-position)
                                       max-drag-position))]
            (if (= drag-position max-drag-position)
              (assoc db :history-cursor nil :history-direction nil)
              (let [old-history-cursor (:history-cursor db)
                    new-direction (if (or (nil? old-history-cursor) (< new-history-cursor old-history-cursor)) :bkwd :fwd)]
                (if (= new-history-cursor old-history-cursor) db
                  (assoc db :history-cursor new-history-cursor :history-direction new-direction)))))))))

; x-position of the history button
(rf/reg-sub :history-button-x
  (fn [db _]
    ; use inc to deal with zeros
    (clamp
      0 (- (:window-width db) (:drag-btn-width db))
      (/ (* (- (inc (or (:history-cursor db) (active-time-updated db))) (active-time-history-start db))
        (- (:window-width db) (:drag-btn-width db)))
          (inc (- (active-time-updated db) (active-time-history-start db)))))))

(rf/reg-event-db :set-history-limit
  (fn [db [_ limit]]
    (-> db
        (assoc :history-limit limit)
        (update :history-cursor
          #(if % (clamp
             (- (active-time-updated db) limit)
             (active-time-updated db) %))))))

; upon the :history-cursor is changed, send a :flo/seek event to server
(w/add-watch-db :drag-changed-internal [:history-cursor] #(rf/dispatch [:drag-changed %4]))
(rf/reg-event-fx :drag-changed
  (fn [{:keys [db]} [_ timestamp]]
    {:chsk-send [:flo/seek [(:active-note-name db) (js/Math.round timestamp)]]}))
