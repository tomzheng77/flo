(ns flo.client.store
  (:require [clojure.data.avl :as avl]
            [reagent.core :as r]
            [re-frame.db :as db]))

(def watches-on-db (r/atom {}))
(defn add-watch-db [ident v listener]
  (swap! watches-on-db #(assoc % ident {:path v :listener listener}))
  (add-watch db/app-db ident
             (fn [a b c d]
               (let [old (get-in c v) new (get-in d v)]
                 (when-not (= old new)
                   (listener a b old new))))))


(defn trigger-watch-db [ident]
  (when (@watches-on-db ident)
    (let [{:keys [path listener]} (@watches-on-db ident)
          value (get-in @db/app-db path)]
      (listener db/app-db ident value value))))


(rf/reg-event-db
  :initialize
  (fn [_ [_ {:keys [all-notes time-created time-updated]}]]
    {:last-shift-press nil ; the time when the shift key was last pressed
     :search           nil ; the active label being searched, nil means no search
     :window-width     (.-innerWidth js/window)
     :drag-btn-width   80
     :drag-timestamp   nil
     :drag-start       nil
     :history          (avl/sorted-map)
     :show-navigation  false
     :all-notes        all-notes
     :time-start       time-created
     :time-last-save   time-updated}))
