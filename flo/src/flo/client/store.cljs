(ns flo.client.store
  (:require [clojure.data.avl :as avl]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-frame.db :as db]
            [clojure.string :as str]))

(def watches-on-db (r/atom {}))
(defn add-watch-db [ident v listener]
  (swap! watches-on-db #(assoc % ident {:path v :listener listener }))
  (add-watch db/app-db ident
    (fn [a b c d]
      (let [old (get-in c v) new (get-in d v)]
        (when-not (= old new)
          (listener a b old new))))))


; accepts an identity, a vector of paths
; as opposed to a single path in add-watch-db
; and a listener function
; where old and new will be vectors
(defn add-watches-db [ident vs listener]
  (swap! watches-on-db #(assoc % ident {:paths vs :listener listener}))
  (add-watch db/app-db ident
    (fn [a b c d]
      (let [old (map #(get-in c %) vs) new (map #(get-in d %) vs)]
        (when-not (= old new)
          (listener a b old new))))))


(defn trigger-watch-db [ident]
  (when (@watches-on-db ident)
    (let [{:keys [path paths listener]} (@watches-on-db ident)
          value (or (and path (get-in @db/app-db path))
                    (and paths (map #(get-in @db/app-db %) paths)))]
      (listener db/app-db ident value value))))

(def db db/app-db)

(rf/reg-event-db
  :initialize
  (fn [_ [_ {:keys [file-id content notes-summary time-created time-updated]}]]
    {:last-shift-press nil ; the time when the shift key was last pressed
     :search           nil ; the active label being searched, nil means no search
     :window-width     (.-innerWidth js/window)
     :drag-btn-width   80
     :drag-timestamp   nil
     :drag-start       nil
     :history          (avl/sorted-map)
     :navigation       nil ; nil means no navigation, "string" means
     :navigation-index nil ; selected item in navigation box
     :notes-list       notes-summary
     :time-start       time-created
     :time-last-save   time-updated
     :file-id          file-id
     :initial-content  (str content)
     :last-save        (str content)}))

(rf/reg-sub :last-shift-press (fn [db v] (:last-shift-press db)))
(rf/reg-sub :search (fn [db v] (:search db)))
(rf/reg-sub :window-width (fn [db v] (:window-width db)))
(rf/reg-sub :drag-btn-width (fn [db v] (:drag-btn-width db)))
(rf/reg-sub :drag-timestamp (fn [db v] (:drag-timestamp db)))
(rf/reg-sub :drag-start (fn [db v] (:drag-start db)))
(rf/reg-sub :history (fn [db v] (:history db)))
(rf/reg-sub :navigation (fn [db v] (:navigation db)))
(rf/reg-sub :notes-list (fn [db v] (:notes-list db)))
(rf/reg-sub :time-start (fn [db v] (:time-start db)))
(rf/reg-sub :time-last-save (fn [db v] (:time-last-save db)))
(rf/reg-sub :file-id (fn [db v] (:file-id db)))
(rf/reg-sub :initial-content (fn [db v] (:initial-content db)))

(rf/reg-event-db :new-save
  (fn [db [_ time]]
    (assoc db :time-last-save time)))

(rf/reg-event-db :window-resize
  (fn [db [_ width height]]
    (assoc db :window-width width)))

(rf/reg-event-db :set-drag-timestamp
  (fn [db [_ t]]
    (assoc db :drag-timestamp t)))

(rf/reg-event-db :set-drag-start
  (fn [db [_ item]]
    (assoc db :drag-start item)))

(rf/reg-event-db :recv-history
  (fn [db [_ timestamp note]]
    (update db :history #(assoc % timestamp note))))

(rf/reg-event-db :shift-press
  (fn [db [_ t]]
    (assoc db :last-shift-press t)))

(rf/reg-event-db :mouse-move
  (fn [db [_ event]]
    (let [mouse-x (:mouse-x event)
          active-drag (:drag-start db)]
      (if-not active-drag db
        (let [dx (- mouse-x (:x active-drag))
              start-position (:position active-drag)
              width (:window-width db)]
          (let [drag-position      (min (max 0 (+ dx start-position)) (- width 80))
                max-drag-position  (- (:window-width db) (:drag-btn-width db))
                new-drag-timestamp (+ (:time-start db)
                                      (/ (* (- (:time-last-save db) (:time-start db)) drag-position)
                                         max-drag-position))]
            (if (= drag-position max-drag-position)
              (assoc db :drag-timestamp nil)
              (assoc db :drag-timestamp new-drag-timestamp))))))))

(rf/reg-sub :drag-btn-x
  (fn [db v]
    (/ (* (- (or (:drag-timestamp db) (:time-last-save db)) (:time-start db))
          (- (:window-width db) (:drag-btn-width db)))
       (- (:time-last-save db) (:time-start db)))))

(rf/reg-event-db :toggle-navigation
  (fn [db v]
    (if (nil? (:navigation db))
      (assoc db :navigation "" :navigation-index nil)
      (assoc db :navigation nil :navigation-index nil))))

(rf/reg-event-db :set-navigation
  (fn [db [_ nav]]
    (assoc db :navigation nav
              :navigation-index nil)))

(rf/reg-sub :navigation-notes
  (fn [db v]
    (filter #(str/includes? (:name %) (:navigation db)) (:notes-list db))))
