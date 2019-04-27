(ns flo.client.store
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require [clojure.data.avl :as avl]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-frame.db :as db]
            [clojure.string :as str]
            [cljs.core.match :refer-macros [match]]))

(defn get-x [db fn-or-vec]
  (if (fn? fn-or-vec)
    (fn-or-vec db)
    (get-in db fn-or-vec)))

(def watches-on-db (r/atom {}))
(defn add-watch-db [ident v listener]
  (swap! watches-on-db #(assoc % ident {:path v :listener listener }))
  (add-watch db/app-db ident
    (fn [a b c d]
      (let [old (get-x c v) new (get-x d v)]
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
      (let [old (map #(get-x c %) vs) new (map #(get-x d %) vs)]
        (when-not (= old new)
          (listener a b old new))))))


(defn trigger-watch-db [ident]
  (when (@watches-on-db ident)
    (let [{:keys [path paths listener]} (@watches-on-db ident)
          value (or (and path (get-x @db/app-db path))
                    (and paths (map #(get-x @db/app-db %) paths)))]
      (listener db/app-db ident value value))))

(def db db/app-db)

(rf/reg-event-db
  :initialize
  (fn [_ [_ time {:keys [note notes-summary]}]]
    {:last-shift-press nil ; the time when the shift key was last pressed
     :search           nil ; the active label being searched, nil means no search
     :window-width     (.-innerWidth js/window)

     :drag-btn-width   80
     :drag-timestamp   nil
     :drag-start       nil

     :navigation       nil ; nil means no navigation, "string" means
     :navigation-index nil ; selected item in navigation box

     ; all the notes organised into a map
     ; including the current note being edited (stored in :active-note-name)
     ; each notes has :name, :time-created, :time-updated and :length
     ; only loaded notes have :content
     :active-note-name (:name note)
     :notes            (let [notes-summary-map (into {} (map (fn [n] [(:name n) n]) notes-summary))]
                         (->> (assoc notes-summary-map (:name note) note)
                              (map (fn [[k v]] [k (assoc v :history (avl/sorted-map))]))
                              (map (fn [[k v]] [k (assoc v :last-save (:content v))]))
                              (into {})))}))

(rf/reg-sub :last-shift-press (fn [db v] (:last-shift-press db)))
(rf/reg-sub :search (fn [db v] (:search db)))
(rf/reg-sub :window-width (fn [db v] (:window-width db)))
(rf/reg-sub :drag-btn-width (fn [db v] (:drag-btn-width db)))
(rf/reg-sub :drag-timestamp (fn [db v] (:drag-timestamp db)))
(rf/reg-sub :drag-start (fn [db v] (:drag-start db)))
(rf/reg-sub :navigation (fn [db v] (:navigation db)))

(defn active-history [db] (get-in db [:notes (:active-note-name db) :history]))
(defn active-time-created [db] (get-in db [:notes (:active-note-name db) :time-created]))
(defn active-time-updated [db] (get-in db [:notes (:active-note-name db) :time-updated]))

(rf/reg-sub :time-start (fn [db v] (get-in db [:notes (:active-note-name db) :time-created])))
(rf/reg-sub :time-last-save (fn [db v] (get-in db [:notes (:active-note-name db) :time-updated])))
(rf/reg-sub :initial-content (fn [db v] (get-in db [:notes (:active-note-name db) :content])))

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
                new-drag-timestamp (+ (active-time-created db)
                                      (/ (* (- (active-time-updated db) (active-time-created db)) drag-position)
                                         max-drag-position))]
            (if (= drag-position max-drag-position)
              (assoc db :drag-timestamp nil)
              (assoc db :drag-timestamp new-drag-timestamp))))))))

(rf/reg-event-db
  :chsk-event
  (fn [db [_ event]]
    (match event
      [:chsk/recv [:flo/history [note]]]
      (assoc-in db [:notes (:active-note-name db) :history (:time-updated note)] (:content note))
      :else db)))

(rf/reg-sub :drag-btn-x
  (fn [db v]
    (/ (* (- (or (:drag-timestamp db) (active-time-updated db)) (active-time-created db))
          (- (:window-width db) (:drag-btn-width db)))
       (- (active-time-updated db) (active-time-created db)))))

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
    (filter #(str/includes? (:name %)(:navigation db)) (map val (:notes db)))))

(rf/reg-event-fx :editor-tick
  (fn [{:keys [db]} [_ content time]]
    (if (= content (:last-save db))
      {:db db}
      {:db (assoc db :last-save content :time-last-save time)
       :chsk-send [:flo/save [(:active-note-name db) content]]})))

(add-watch-db :drag-changed [:drag-timestamp] (fn [_ _ _ timestamp] (rf/dispatch [:drag-changed timestamp])))
(rf/reg-event-fx
  :drag-changed
  (fn [{:keys [db]} [_ timestamp]]
    {:chsk-send [:flo/seek [(:active-note-name db) (js/Math.round timestamp)]]}))
