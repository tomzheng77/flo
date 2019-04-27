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
  (fn [_ [_ time {:keys [active-note-name notes]}]]
    {:last-shift-press nil ; the time when the shift key was last pressed
     :search           nil ; the active label being searched, nil means no search
     :window-width     (.-innerWidth js/window)

     :drag-btn-width   80
     :history-cursor   nil
     :drag-start       nil

     :navigation       nil ; nil means no navigation, "string" means
     :navigation-index nil ; selected item in navigation box

     ; all the notes organised into a map
     ; including the current note being edited (stored in :active-note-name)
     ; each notes has :name, :time-created, :time-updated and :length
     ; :content is provided by the server initially, then synced from the editor at a fixed interval
     :active-note-name active-note-name
     :notes            (->> notes
                            (map (fn [n] [(:name n) n]) notes)
                            (map (fn [[k v]] [k (assoc v :history (avl/sorted-map))]))
                            (into {})
                            ((fn [m] (if (get m active-note-name) m
                                (assoc m active-note-name
                                  {:name active-note-name
                                   :time-created (- time 1)
                                   :time-updated time
                                   :length 0
                                   :content ""
                                   :history (avl/sorted-map)})))))}))

(rf/reg-sub :last-shift-press (fn [db v] (:last-shift-press db)))
(rf/reg-sub :search (fn [db v] (:search db)))
(rf/reg-sub :window-width (fn [db v] (:window-width db)))
(rf/reg-sub :drag-btn-width (fn [db v] (:drag-btn-width db)))
(rf/reg-sub :history-cursor (fn [db v] (:history-cursor db)))
(rf/reg-sub :drag-start (fn [db v] (:drag-start db)))
(rf/reg-sub :navigation (fn [db v] (:navigation db)))

(defn active-history [db] (get-in db [:notes (:active-note-name db) :history]))
(defn active-time-created [db] (get-in db [:notes (:active-note-name db) :time-created]))
(defn active-time-updated [db] (get-in db [:notes (:active-note-name db) :time-updated]))

(rf/reg-sub :active-time-updated (fn [db v] (get-in db [:notes (:active-note-name db) :time-updated])))
(rf/reg-sub :initial-content (fn [db v] (get-in db [:notes (:active-note-name db) :content])))

(rf/reg-event-db :window-resize (fn [db [_ width _]] (assoc db :window-width width)))
(rf/reg-event-db :start-drag (fn [db [_ item]] (assoc db :drag-start item)))
(rf/reg-event-db :shift-press (fn [db [_ t]] (assoc db :last-shift-press t)))

; whenever the mouse has been moved
(rf/reg-event-db :mouse-move
  (fn [db [_ event]]
    (let [drag-start (:drag-start db)]
      (if-not drag-start db
        (let [dx (- (:mouse-x event) (:mouse-x drag-start))
              start-position (:btn-x drag-start)
              width (:window-width db)
              drag-position (min (max 0 (+ dx start-position)) (- width 80))
              max-drag-position (- (:window-width db) (:drag-btn-width db))
              new-history-cursor (+ (active-time-created db)
                                    (/ (* (- (active-time-updated db) (active-time-created db)) drag-position)
                                       max-drag-position))]
            (if (= drag-position max-drag-position)
              (assoc db :history-cursor nil)
              (assoc db :history-cursor new-history-cursor)))))))

; whenever a message has been received from sente
(rf/reg-event-db
  :chsk-event
  (fn [db [_ event]]
    (match event
      [:chsk/recv [:flo/history [note]]]
      (assoc-in db [:notes (:active-note-name db) :history (:time-updated note)] (:content note))
      :else db)))

; x-position of the history button
(rf/reg-sub :history-btn-x
  (fn [db _]
    (println db)
    (/ (* (- (or (:history-cursor db) (active-time-updated db)) (active-time-created db))
          (- (:window-width db) (:drag-btn-width db)))
       (- (active-time-updated db) (active-time-created db)))))

; turns navigation on/off
(rf/reg-event-db :toggle-navigation
  (fn [db v]
    (if (nil? (:navigation db))
      (assoc db :navigation "" :navigation-index nil)
      (assoc db :navigation nil :navigation-index nil))))

; when the user enters something into the navigation search box
; or when navigation is turned on/off
(rf/reg-event-db :navigation-input
  (fn [db [_ nav]]
    (assoc db :navigation nav :navigation-index nil)))

; list of notes to display after passing through the navigation filter
(rf/reg-sub :navigation-list
  (fn [db _]
    (filter #(str/includes? (:name %)(:navigation db)) (map val (:notes db)))))

(rf/reg-event-fx :navigation-select
  (fn [{:keys [db]} [_ note]]
    {:db (assoc db :active-note-name (:name note)
                   :drag-start nil
                   :history-cursor nil)
     :editor (:content note)}))

; called with the editor's contents every second
(rf/reg-event-fx :editor-tick
  (fn [{:keys [db]} [_ content time]]
    (if (= content (get-in db [:notes (:active-note-name db) :content]))
      {:db db}
      {:db (-> db (assoc-in [:notes (:active-note-name db) :content] content)
                  (assoc-in [:notes (:active-note-name db) :time-updated] time))
       :chsk-send [:flo/save [(:active-note-name db) content]]})))

(add-watch-db :drag-changed [:history-cursor] (fn [_ _ _ timestamp] (rf/dispatch [:drag-changed timestamp])))
(rf/reg-event-fx
  :drag-changed
  (fn [{:keys [db]} [_ timestamp]]
    {:chsk-send [:flo/seek [(:active-note-name db) (js/Math.round timestamp)]]}))
