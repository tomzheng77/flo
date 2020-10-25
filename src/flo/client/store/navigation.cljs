(ns flo.client.store.navigation
  (:require [clojure.string :as str]
            [re-frame.core :as rf]
            [flo.client.model.selection :as s]
            [flo.client.functions :refer [clamp]]))

;;;; contains the backend for the navigation UI component and its related actions

;; parses the text which the user entered into the navigation box
;; into separate components, such as name and search keyword
;; by default, the query is the name (or abbreviation) of a note
;; if it contains a '@', then the part after the '@' will be treated as a search
;; if it contains a ':', then the part after the ':' will be treated as a selection range
(defn parse-navigation-query [query]
  (if-not query
    {:name nil :search nil :range nil}
    (cond
      (re-find #"@" query)
      (let [[name search] (str/split query #"@" 2)]
        {:name name
         :search
         (if (str/starts-with? search "!")
             (str/upper-case (subs search 1))
             (str (str/upper-case search) "="))})

      (re-find #":" query)
      (let [[name range-str] (str/split query #":" 2)]
        {:name name
         :range (s/str-to-range range-str)})

      :else {:name query})))

; turns navigation on/off
(rf/reg-event-fx :toggle-navigation
  (fn [{:keys [db]} [_ init]]
    (if (nil? (:navigation db))
      {:db db :dispatch [:navigation-input (or init "")]}
      {:db db :dispatch [:navigation-input nil]})))

(defn navigation-list [db]
  (let [{:keys [name]} (parse-navigation-query (:navigation db))
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
    (let [search-subquery (:search (parse-navigation-query new-input))
          range-subquery (:range (parse-navigation-query new-input))
          old-input (:navigation db)
          old-name (:name (parse-navigation-query old-input))
          new-name (:name (parse-navigation-query new-input))
          old-index (:navigation-index db)

          ; attempt to find the index of the note which has been selected
          ; inside the new list
          old-navs (if old-input (navigation-list db))
          new-navs (if new-input (navigation-list (assoc db :navigation new-input)))
          nav-name (if old-index (:name (nth old-navs old-index)))
          new-index (if (and new-navs nav-name) (ffirst (filter (fn [[_ nav]] (= nav-name (:name nav))) (map-indexed vector new-navs))))
          new-db (assoc db :navigation new-input
                           ; if navigation is turned off or the name has been changed, reset the index
                           :navigation-index (if (= old-name new-name) old-index new-index)
                           :preview-selection (s/range-to-selection range-subquery)
                           :search (or (if search-subquery (str/upper-case search-subquery)) (:search db)))]
      (if (:navigation new-db)
        {:db new-db}
        {:db new-db :focus-editor true}))))

; updates the navigation index using a fn
(defn update-navigation-index-fx [db f]
  (let [index (:navigation-index db)
        navs (navigation-list db)
        max-index (dec (count navs))
        new-index (if-not index 0 (clamp 0 max-index (f index)))
        note (nth navs new-index)
        note-with-selection (assoc note :selection (or (:preview-selection db) (:selection note)))]
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
          search (:search (parse-navigation-query (or navigation (:navigation db))))]
      (if-not note
        {:db (assoc db :search search)}
        {:db (assoc db :search search) :dispatch [:request-open-note note]}))))

; either navigates to a result of the :navigation query based on :navigation-index
; or navigates based on name only
(rf/reg-event-fx :navigate-enter
  (fn [{:keys [db]} [_ time]]
    (let [navs (navigation-list db)]
      (if (:navigation-index db)
        ; since a navigation index is stored, the user must have pressed
        ; the down button in order to render a preview, therefore the note can be
        ; displayed by copying from the preview editor
        {:db db :dispatch [:request-open-note (nth navs (:navigation-index db))]}

        ; otherwise navigate to name
        (let [{:keys [name]} (parse-navigation-query (:navigation db))]
          (if (or (nil? name) (empty? name))
            {:db (assoc db :navigation nil :navigation-index nil) :focus-editor true}
            {:db db :dispatch [:request-open-note name]}))))))
