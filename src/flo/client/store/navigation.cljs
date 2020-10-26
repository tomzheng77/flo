(ns flo.client.store.navigation
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [flo.client.model.note :as n]
            [flo.client.model.selection :as s]
            [flo.client.model.query :as q]
            [flo.client.functions :refer [clamp]]))

;;;; contains the backend for the navigation UI component and its related actions

;; turns navigation on/off
(rf/reg-event-fx :toggle-navigation
  (fn [{:keys [db]} [_ init]]
    (if (nil? (:navigation db))
      {:db db :dispatch [:navigation-input (or init "")]}
      {:db db :dispatch [:navigation-input nil]})))

(defn navigation-list [db]
  (let [{:keys [keyword]} (q/parse (:navigation db))
        ntag (if keyword (str/upper-case keyword))]
    (->> (map val (:notes db))
         (filter
           #(or
             (str/includes? (:name %) (or keyword ""))
             (= (:ntag %) (or ntag ""))))
         (sort-by
           #(vector
             (not= 0 (count (:content %)))
             (= (:ntag %) (or ntag ""))
             (:time-updated %)))
         (reverse))))

(rf/reg-sub :navigation (fn [db v] (:navigation db)))
(rf/reg-sub :navigation-index (fn [db v] (:navigation-index db)))

; list of notes to display after passing through the navigation filter
(rf/reg-sub :navigation-list
  (fn [db _]
    (let [at (:navigation-index db)]
      (for [[index note] (map-indexed vector (navigation-list db))]
        (assoc note :focus (= index at))))))

; when the user enters something into the navigation search box
; or when navigation is turned on/off
(rf/reg-event-fx :navigation-input
  (fn [{:keys [db]} [_ new-input]]
    (let [search-subquery (:search (q/parse new-input))
          selection-subquery (:selection (q/parse new-input))
          old-input (:navigation db)
          old-name (:keyword (q/parse old-input))
          new-name (:keyword (q/parse new-input))
          old-index (:navigation-index db)

          ; attempt to find the index of the note which has been selected
          ; inside the new list
          old-navs (if old-input (navigation-list db))
          new-navs (if new-input (navigation-list (assoc db :navigation new-input)))
          old-note (if old-index (nth old-navs old-index))
          nav-name (:name old-note)
          new-index (if (and new-navs nav-name) (ffirst (filter (fn [[_ nav]] (= nav-name (:name nav))) (map-indexed vector new-navs))))
          new-db (assoc db :navigation new-input
                           ; if navigation is turned off or the name has been changed, reset the index
                           :navigation-index (if (= old-name new-name) old-index new-index))]
      (if (:navigation new-db)
        (if old-note
          {:db new-db :preview-note
           [(-> old-note
                (n/note-select-first-occurrence search-subquery)
                (n/note-set-selection selection-subquery))]}
          {:db new-db})
        {:db new-db :focus-editor true}))))

; updates the navigation index using a fn
(defn update-navigation-index-fx [db f]
  (let [index (:navigation-index db)
        navs (navigation-list db)
        max-index (dec (count navs))
        new-index (if-not index 0 (clamp 0 max-index (f index)))
        note (nth navs new-index)
        selection (:selection (q/parse (:navigation db)))
        note-with-selection (n/note-set-selection note selection)]
    {:db (assoc db :navigation-index new-index)
     :preview-note [note-with-selection]}))

; navigates to the item above/below
(rf/reg-event-fx :navigate-up (fn [{:keys [db]} _] (update-navigation-index-fx db dec)))
(rf/reg-event-fx :navigate-down (fn [{:keys [db]} _] (update-navigation-index-fx db inc)))

; navigates to the first result of the :navigation query
; regardless of :navigation-index
(rf/reg-event-fx :navigate-direct
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]} [_ navigation create-if-not-exist?]]
    (let [db-set-nav (update db :navigation #(or navigation %))
          {:keys [keyword search selection]} (q/parse (:navigation db-set-nav))]
      (let [navs (navigation-list db-set-nav)
            note (if keyword (first navs) (get-in db [:notes (:active-note-name db)]))
            note-with-selection
            (-> note
                (n/note-select-first-occurrence search)
                (n/note-set-selection selection))]
        (if-not note
          (if-not create-if-not-exist? {:db db}
            {:db db :dispatch [:request-open-note keyword]})
          {:db (-> db (assoc-in [:notes (:name note)] note-with-selection))
           :dispatch [:request-open-note note-with-selection]})))))

; either navigates to a result of the :navigation query based on :navigation-index
; or navigates based on name only
(rf/reg-event-fx :navigate-enter
  (fn [{:keys [db]} [_]]
    (let [navs (navigation-list db)]
      (if (:navigation-index db)
        ; since a navigation index is stored, the user must have pressed
        ; the down button in order to render a preview, therefore the note can be
        ; displayed by copying from the preview editor
        (let [note (nth navs (:navigation-index db))
              {:keys [search selection]} (q/parse (:navigation db))
              note-with-selection
              (-> note
                  (n/note-select-first-occurrence search)
                  (n/note-set-selection selection))]
          {:db (assoc-in db [:notes (:name note)] note-with-selection)
           :dispatch [:request-open-note note-with-selection]})

        ; otherwise navigate to name
        (let [{:keys [keyword search selection]} (q/parse (:navigation db))]
          (if (or (nil? keyword) (empty? keyword))
            {:db (assoc db :navigation nil :navigation-index nil) :focus-editor true}
            (if (get-in db [:notes keyword])
              {:db (update-in db [:notes keyword]
                    #(-> % (n/note-select-first-occurrence search)
                           (n/note-set-selection selection)))
               :dispatch [:request-open-note keyword]}
              {:db db :dispatch [:request-open-note keyword]})))))))
