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
  (let [{:keys [name]} (q/parse (:navigation db))
        ntag (if name (str/upper-case name))]
    (->> (map val (:notes db))
         (filter
           #(or
             (str/includes? (:name %) (or name ""))
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
          old-name (:name (q/parse old-input))
          new-name (:name (q/parse new-input))
          old-index (:navigation-index db)

          ; attempt to find the index of the note which has been selected
          ; inside the new list
          old-navs (if old-input (navigation-list db))
          new-navs (if new-input (navigation-list (assoc db :navigation new-input)))
          old-nav (if old-index (nth old-navs old-index))
          nav-name (:name old-nav)
          new-index (if (and new-navs nav-name) (ffirst (filter (fn [[_ nav]] (= nav-name (:name nav))) (map-indexed vector new-navs))))
          new-db (assoc db :navigation new-input
                           ; if navigation is turned off or the name has been changed, reset the index
                           :navigation-index (if (= old-name new-name) old-index new-index)
                           :search (or (if search-subquery (str/upper-case search-subquery)) (:search db)))]
      (if (:navigation new-db)
        (if old-nav
          {:db new-db :preview-note [(n/note-set-selection old-nav selection-subquery)]}
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
     :preview-note [note-with-selection (:search db)]}))

; navigates to the item above/below
(rf/reg-event-fx :navigate-up (fn [{:keys [db]} _] (update-navigation-index-fx db dec)))
(rf/reg-event-fx :navigate-down (fn [{:keys [db]} _] (update-navigation-index-fx db inc)))

; navigates to the first result of the :navigation query
; regardless of :navigation-index
(rf/reg-event-fx :navigate-direct
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]} [_ navigation]]
    (let [navs (navigation-list (update db :navigation #(or navigation %)))
          note (first navs)
          search (:search (q/parse (or navigation (:navigation db))))
          selection (:selection (q/parse (:navigation db)))
          note-with-selection (n/note-set-selection note selection)]
      (if-not note
        {:db (assoc db :search search)}
        {:db (-> db (assoc :search search) (assoc-in [:notes (:name note)] note))
         :dispatch [:request-open-note note-with-selection]}))))

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
              selection (:selection (q/parse (:navigation db)))
              note-with-selection (n/note-set-selection note selection)]
          {:db (assoc-in db [:notes (:name note)] note-with-selection)
           :dispatch [:request-open-note note-with-selection]})

        ; otherwise navigate to name
        (let [{:keys [name selection]} (q/parse (:navigation db))]
          (if (or (nil? name) (empty? name))
            {:db (assoc db :navigation nil :navigation-index nil) :focus-editor true}
            {:db (update-in db [:notes name] #(n/note-set-selection % selection))
             :dispatch [:request-open-note name]}))))))
