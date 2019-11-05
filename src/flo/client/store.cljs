(ns flo.client.store
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require [clojure.data.avl :as avl]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-frame.db :as db]
            [clojure.string :as str]
            [cljs.core.match :refer-macros [match]]
            [flo.client.functions :refer [find-all]]
            [clojure.set :as set]
            [flo.client.constants :as c]))

; determines a tag for the note
(defn find-ntag [content]
  (let [search (re-find #"\[&[A-Z0-9]+=\]" content)]
    (if search (subs search 2 (dec (dec (count search)))))))

(defn clamp [min max x]
  (if (< x min)
    min
    (if (> x max) max x)))

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
(defn new-note [name time]
  {:type :note
   :name name
   :time-created time
   :time-updated time
   :time-changed time
   :content ""
   :history (avl/sorted-map)
   :selection {:row 0 :column 0}
   :ntag nil})

(defn parse-navigation-query [query]
  (if-not query
    {:name nil :search nil}
    (let [[name search] (str/split query #"@" 2)]
      {:name name
       :search
       (if (not-empty search)
         (if (str/starts-with? search "!")
           (str/upper-case search)
           (str (str/upper-case search) "=")))})))

(def name-length-limit 100)
(rf/reg-event-fx
  :initialize
  (fn [_ [_ time {:keys [notes read-only]} href]]
    (let [active-note-name (or (re-find c/url-hash-regex href) "default")
          notes-valid (filter #(> name-length-limit (count (:name %))) notes)]
      {:dispatch [:open-note active-note-name]
       :db
       {:last-shift-press nil ; the time when the shift key was last pressed
        :search           nil ; the active label being searched, nil means no search
        :window-width     (.-innerWidth js/window)

        ; if this flag is set to true
        ; changes [:flo/save [name content]] will not be sent
        ; refreshes [:flo/refresh note] will not be handled
        :read-only        read-only

        :drag-btn-width   80
        :history-cursor   nil
        :history-direction nil ; last direction the history cursor was moved in #{nil :bkwd :fwd}
        :drag-start       nil

        ; amount of history to allow scroll back, in milliseconds
        :history-limit    (* 1000 60 60 24)

        ; global navigation query
        ; consists of a name and location part
        ; e.g. "fl@fx" means
        ;   go to the note with either ntag or name "FL"
        ;   within it search for [FX] or [FX=]
        ; e.g. "fl@fx=" means
        ;   ...
        ;   within it search for [FX=]
        ; e.g. "fl:100" means
        ;   ...
        ;   within it go to line 100
        :navigation       nil
        :navigation-index nil ; selected item in navigation box
        :image-upload     nil

        ; all the notes organised into a map
        ; including the current note being edited (stored in :active-note-name)
        ; each notes has :name, :time-created, :time-updated
        ; :content is provided by the server initially, then synced from the editor at a fixed interval
        ; :selection {:row :column} contains the location of the cursor, initially set to 0, 0
        :active-note-name active-note-name
        :notes            (->> notes-valid
                               (map #(assoc % :type :note))
                               (map #(assoc % :selection {:row 0 :column 0}))
                               (map #(assoc % :ntag (find-ntag (:content %))))
                               (map (fn [n] [(:name n) n]))
                               (map (fn [[k v]] [k (assoc v :history (avl/sorted-map))]))
                               (into {})
                               ((fn [m] (if (get m active-note-name) m
                                 (assoc m active-note-name
                                   (new-note active-note-name time))))))}})))

(defn active-history [db] (get-in db [:notes (:active-note-name db) :history]))
(defn active-time-updated [db] (get-in db [:notes (:active-note-name db) :time-updated]))
(defn active-time-created [db] (get-in db [:notes (:active-note-name db) :time-created]))
(defn active-time-history-start [db]
  (let [updated (active-time-updated db)]
    (clamp (- updated (:history-limit db)) updated (active-time-created db))))

(rf/reg-sub :last-shift-press (fn [db v] (:last-shift-press db)))
(rf/reg-sub :search (fn [db v] (:search db)))
(rf/reg-sub :window-width (fn [db v] (:window-width db)))
(rf/reg-sub :drag-btn-width (fn [db v] (:drag-btn-width db)))
(rf/reg-sub :history-cursor (fn [db v] (:history-cursor db)))
(rf/reg-sub :drag-start (fn [db v] (:drag-start db)))
(rf/reg-sub :navigation (fn [db v] (:navigation db)))
(rf/reg-sub :navigation-index (fn [db v] (:navigation-index db)))
(rf/reg-sub :image-upload (fn [db v] (:image-upload db)))
(rf/reg-sub :history-limit (fn [db v] (:history-limit db)))

(rf/reg-event-db :set-search (fn [db [_ search]] (assoc db :search search)))
(rf/reg-event-db :swap-search (fn [db [_ f]] (update db :search f)))
(rf/reg-event-db :set-history-limit
  (fn [db [_ limit]]
    (-> db
        (assoc :history-limit limit)
        (update :history-cursor
          #(if % (clamp
             (- (active-time-updated db) limit)
             (active-time-updated db) %))))))

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
              new-history-cursor (+ (active-time-history-start db)
                                    (/ (* (- (active-time-updated db) (active-time-history-start db)) drag-position)
                                       max-drag-position))]
            (if (= drag-position max-drag-position)
              (assoc db :history-cursor nil :history-direction nil)
              (let [old-history-cursor (:history-cursor db)
                    new-direction (if (or (nil? old-history-cursor) (< new-history-cursor old-history-cursor)) :bkwd :fwd)]
                (if (= new-history-cursor old-history-cursor) db
                  (assoc db :history-cursor new-history-cursor :history-direction new-direction)))))))))

(defn exists-newer-note [db {:keys [name time]}]
  (let [existing-note (get-in db [:notes name])]
    (if existing-note
      (or (> (:time-updated existing-note) time)
          (> (:time-changed existing-note) time)))))

; whenever a message has been received from sente
(rf/reg-event-fx
  :chsk-event
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]} [_ event]]
    (match event
      [:chsk/recv [:flo/history note]]
      {:db (assoc-in db [:notes (:active-note-name db) :history (:time-updated note)] (:content note))}
      [:chsk/recv [:flo/refresh note]]
      (if (or (:read-only db) (exists-newer-note db note))
        {:db db}
        (conj {:db (let [existing-note (or (get-in db [:notes (:name note)]) (new-note (:name note) time))]
                     (-> db (assoc-in [:notes (:name note)] (set/union existing-note note))))}
              (when (= (:name note) (:active-note-name db)) [:refresh-editor (:content note)])))
      :else {:db db})))

; x-position of the history button
(rf/reg-sub :history-button-x
  (fn [db _]
    ; use inc to deal with zeros
    (clamp
      0 (- (:window-width db) (:drag-btn-width db))
      (/ (* (- (inc (or (:history-cursor db) (active-time-updated db))) (active-time-history-start db))
        (- (:window-width db) (:drag-btn-width db)))
          (inc (- (active-time-updated db) (active-time-history-start db)))))))

(rf/reg-event-fx :hash-change
  (fn [{:keys [db]} [_ new-url]]
    (let [note-name (re-find c/url-hash-regex new-url)]
      (if note-name
        {:db db :dispatch [:open-note note-name]}
        {:db db}))))

(rf/reg-event-db :toggle-image-upload
  (fn [db _]
    (update db :image-upload not)))

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

; when the user enters something into the navigation search box
; or when navigation is turned on/off
(rf/reg-event-fx :navigation-input
  (fn [{:keys [db]} [_ new-input]]
    (let [search-subquery (:search (parse-navigation-query new-input))
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
                           :search (or (and search-subquery (str/upper-case search-subquery))
                                       (:search db)))]
      (if (:navigation new-db)
        {:db new-db}
        {:db new-db :focus-editor true}))))

; updates the navigation index using a fn
(defn update-navigation-index-fx [db f]
  (let [index (:navigation-index db)
        navs (navigation-list db)
        max-index (dec (count navs))
        new-index (if-not index 0 (clamp 0 max-index (f index)))
        note (nth navs new-index)]
    {:db (assoc db :navigation-index new-index)
     :reset-editor-ro [(:content note) (:search db) (:selection note)]}))

; navigates to the item above/below
(rf/reg-event-fx :navigate-up (fn [{:keys [db]} _] (update-navigation-index-fx db dec)))
(rf/reg-event-fx :navigate-down (fn [{:keys [db]} _] (update-navigation-index-fx db inc)))

(rf/reg-cofx :time
  (fn [coeffects _]
    (assoc coeffects :time (+ (.getTime (js.Date.)) (js/Math.random)))))

; [TAG-SYNTAX]
(rf/reg-event-fx :click-link
  [(rf/inject-cofx :time)]
  (fn [{:keys [time]} [_ types text]]
    (cond
      (types "declaration") {:dispatch [:set-search (-> text c/remove-brackets c/declaration-to-definition)]}
      (types "definition") {:dispatch [:set-search (-> text c/remove-brackets c/definition-to-declaration)]}
      (types "reference") {:dispatch [:navigate-direct (-> text c/remove-brackets)]}
      (types "link") {:open-window text})))

(rf/reg-fx :open-window
  (fn [url]
    (js/window.open url "_blank")))

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
        {:db (assoc db :search search) :dispatch [:open-note note false]}))))

; either navigates to a result of the :navigation query based on :navigation-index
; or navigates based on name only
(rf/reg-event-fx :navigate-enter
  (fn [{:keys [db]} [_ time]]
    (let [navs (navigation-list db)]
      (if (:navigation-index db)
        ; must have already previewed, can copy from read-only editor
        {:db db :dispatch [:open-note (nth navs (:navigation-index db)) true]}

        ; otherwise navigate to name
        (let [{:keys [name]} (parse-navigation-query (:navigation db))]
          (if (or (nil? name) (empty? name))
            {:db (assoc db :navigation nil :navigation-index nil) :focus-editor true}
            {:db db :dispatch [:open-note name false]}))))))

; list of notes to display after passing through the navigation filter
(rf/reg-sub :navigation-list
  (fn [db _]
    (let [at (:navigation-index db)]
      (for [[index note] (map-indexed vector (navigation-list db))]
        (assoc note :focus (= index at))))))

; copy from the read-only editor whenever possible
; otherwise, if the copy-from-ro flag is not set to true
; then the editor state will be explicitly set
(rf/reg-event-fx :open-note
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]} [_ indicator copy-from-ro]]
    (cond
      (string? indicator)
      (let [name indicator
            existing-note (get (:notes db) name)]
        (if existing-note
          {:db db :dispatch [:open-note existing-note false]}
          (let [a-new-note (new-note name time)]
            {:db       (assoc-in db [:notes name] a-new-note)
             :dispatch [:open-note a-new-note false]})))

      (and (map? indicator) (= :note (:type indicator)))
      (let [note indicator
            fx {:set-title (:name note)
                :set-hash (:name note)
                :db (-> db
                     (assoc :active-note-name (:name note))
                     (assoc :drag-start nil)
                     (assoc :history-cursor nil)
                     (assoc :history-direction nil)
                     (assoc :navigation nil)
                     (assoc :navigation-index nil))}]
        (if-not copy-from-ro
          (assoc fx :reset-editor [(:name note) (:content note) (:search db) (:selection note)])
          (assoc fx :reset-editor-from-ro (:name note)))))))

; called with the editor's contents every second
(rf/reg-event-fx :editor-tick
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]} [_ name content]]
    (if (or (empty? name) (= content (get-in db [:notes name :content])))
      {:db db}
      {:db (-> db
               (assoc-in [:notes name :ntag] (find-ntag content))
               (assoc-in [:notes name :content] content)
               (assoc-in [:notes name :time-updated] time))
       :chsk-send
       (if-not (:read-only db) [:flo/save [name time content]])})))

(rf/reg-event-fx :change
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]}]
    {:db (assoc-in db [:notes (:active-note-name db) :time-changed] time)}))

; called whenever the selection of the active note has been changed
(rf/reg-event-db :change-selection
  (fn [db [_ selection]]
    (assoc-in db [:notes (:active-note-name db) :selection] selection)))

; used only to trigger sending the :flo/seek event to server
(add-watch-db :drag-changed-internal [:history-cursor] #(rf/dispatch [:drag-changed %4]))
(rf/reg-event-fx :drag-changed
  (fn [{:keys [db]} [_ timestamp]]
    {:chsk-send [:flo/seek [(:active-note-name db) (js/Math.round timestamp)]]}))

; whether the read-only editor should be shown
(rf/reg-sub :read-only-visible
  #(or (:history-cursor %1) (:navigation-index %1)))

(rf/reg-event-fx :open-history
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]}]
    (let [timestamp (or (:history-cursor db) time)
          time-string (.format (js/moment timestamp) "YYYY-MM-DDTHH:mm:ss")
          note-name (:active-note-name db)
          path (str "/history?t=" time-string "#" note-name)]
      {:db db :open-window path})))

(rf/reg-fx :open-window
  (fn [path]
    (.open js/window path "_blank")))