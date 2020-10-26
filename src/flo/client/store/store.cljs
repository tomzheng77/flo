(ns flo.client.store.store
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require [clojure.data.avl :as avl]
            [flo.client.editor.editor :as editor]
            [flo.client.model.note :as n]
            [flo.client.model.selection :as s]
            [flo.client.model.query :as q]
            [reagent.core :as r]
            [re-frame.core :as rf]
            [re-frame.db :as db]
            [cljs.core.match :refer-macros [match]]
            [flo.client.functions :refer [find-all clamp]]
            [clojure.set :as set]
            [flo.client.constants :as c]
            [flo.client.store.navigation]
            [flo.client.store.history]))

; TODO: remove the old :search related stuff
; TODO: and replace it with either changing the :selection
; TODO: before entering a different note
; TODO: or in-editor actions, such as navigating
; TODO: to the next tag of the same type (ctrl+shift+up/down)

(def plugin-note-name "plugins.js")

;; scans through the note for the first occurrence of
;; an ntag e.g. [&A=] (navigation/note tag)
(defn find-ntag [content]
  (let [ntag-raw (re-find #"\[&[A-Z0-9]+=\]" content)]
    (if ntag-raw (subs ntag-raw 2 (dec (dec (count ntag-raw)))))))

(def name-length-limit 100)
(rf/reg-event-fx
  :initialize
  (fn [_ [_ time {:keys [notes read-only]} href]]
    (let [hash-text (re-find c/regex-url-hash-part href)
          init-note-name "default"
          notes-valid (filter #(> name-length-limit (count (:name %))) notes)
          plugins-js 
          (:content (first (filter #(= (:name %) plugin-note-name) notes)))]
      {:dispatch [:navigate-direct hash-text true]
       :eval-plugins-js plugins-js
       :db
       {:window-width      (.-innerWidth js/window)

        ; if this flag is set to true
        ; changes [:flo/save [name content]] will not be sent
        ; refreshes [:flo/refresh note] will not be handled
        :read-only         read-only

        :drag-btn-width    80
        :history-cursor    nil
        :history-direction nil ; last direction the history cursor was moved in #{nil :bkwd :fwd}
        :drag-start        nil

        ; amount of history to allow scroll back, in milliseconds
        :history-limit     (* 1000 60 60 24)
        :status-text       "Welcome to FloNote"

        ; when set, the client will prefer to open each note
        ; using the table mode
        ; the client may still show a table even if this is not set
        :table-on          false

        ; when set, a terminal window should be shown
        :show-terminal     false

        ; when set, history will not be shown smoothly
        :fast-mode         false

        ; when set, changes will be saved at regular intervals
        :autosave          true

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
        :navigation        nil
        :navigation-index  nil ; selected item in navigation box
        :image-upload      nil

        ; all the notes organised into a map
        ; including the current note being edited (associated with (:active-note-name db))
        ; see flo.client.model.note
        :active-note-name  init-note-name
        :notes             (->> notes-valid
                                (map #(assoc % :type :note))
                                (map #(assoc % :selection nil))
                                (map #(assoc % :ntag (find-ntag (:content %))))
                                (map #(assoc % :history (avl/sorted-map)))
                                (map (fn [n] [(:name n) n]))
                                (into {})
                                ((fn [m] (if (get m init-note-name) m
                                  (assoc m init-note-name
                                     (n/new-note init-note-name time))))))}})))

(rf/reg-sub :window-width (fn [db v] (:window-width db)))
(rf/reg-sub :image-upload (fn [db v] (:image-upload db)))
(rf/reg-sub :status-text (fn [db v] (:status-text db)))

(rf/reg-sub :table-on (fn [db v] (:table-on db)))
(rf/reg-sub :show-terminal (fn [db v] (:show-terminal db)))
(rf/reg-sub :autosave (fn [db v] (:autosave db)))

(rf/reg-event-fx :toggle-table-on (fn [{:keys [db]} [_]]
  {:db (update db :table-on not) :change-editor (not (:table-on db))}))

(rf/reg-event-fx :set-table-on (fn [{:keys [db]} [_ table-on? cascade?]]
  (if cascade?
    {:db (assoc db :table-on table-on?) :change-editor table-on?}
    {:db (assoc db :table-on table-on?)})))

(rf/reg-event-db :toggle-image-upload (fn [db _] (update db :image-upload not)))
(rf/reg-event-db :toggle-show-terminal (fn [db [_]] (update db :show-terminal not)))
(rf/reg-event-db :toggle-autosave (fn [db [_]] (update db :autosave not)))

(rf/reg-sub :active-note-name (fn [db v] (:active-note-name db)))
(rf/reg-sub :active-time-updated (fn [db v] (get-in db [:notes (:active-note-name db) :time-updated])))
(rf/reg-sub :initial-content (fn [db v] (get-in db [:notes (:active-note-name db) :content])))

(rf/reg-event-db :window-resize (fn [db [_ width _]] (assoc db :window-width width)))

; runs the contents of plugins.js
; should add functions to the window.plugins object
; if editor is in the :editor-ace mode
; then returns the active ace instance
; editor.getActiveAce();
; editor.getContent();
; editor.setContent(content);
(rf/reg-fx :eval-plugins-js 
  (fn [js-code]
    (when js-code
      (set! (.-plugins js/window) (clj->js {}))
      (set! (.-editor js/window) (clj->js {
       :getActiveAce #(editor/get-active-ace)}))
      (js/eval js-code))))

(rf/reg-fx :run-plugin
  (fn [plugin-name]
    (.runPlugin (.-plugins js/window) plugin-name)))

(defn exists-newer-note [db {:keys [name time]}]
  (let [existing-note (get-in db [:notes name])]
    (if existing-note
      (or (> (:time-updated existing-note) time)
          (> (:time-changed existing-note) time)))))

; whenever a message has been received from the backend via sente
(rf/reg-event-fx
  :chsk-event
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]} [_ event]]
    (match event
      [:chsk/recv [:flo/saved [name timestamp]]]
      {:db (assoc db :status-text (str "saved " name " at " (.format (js/moment timestamp) "YYYY-MM-DD HH:mm:ss.SSS")))}
      [:chsk/recv [:flo/history note]]
      {:db (assoc-in db [:notes (:active-note-name db) :history (:time-updated note)] (:content note))}
      [:chsk/recv [:flo/refresh note]]
      (if (or (:read-only db) (exists-newer-note db note))
        {:db db}
        (conj {:db (let [existing-note (or (get-in db [:notes (:name note)]) (n/new-note (:name note) time))]
                     (-> db (assoc-in [:notes (:name note)] (set/union existing-note note))))}
              (when (= (:name note) (:active-note-name db)) [:accept-external-change note])))
      :else {:db db})))

; produces the current time in milliseconds since epoch
(rf/reg-cofx :time
  (fn [coeffects _]
    (assoc coeffects :time (+ (.getTime (js.Date.)) (js/Math.random)))))

; triggered whenever the user clicks on a link from the editor
; [TAG-SYNTAX]
(rf/reg-event-fx :click-link
  [(rf/inject-cofx :time)]
  (fn [{:keys [time]} [_ types text]]
    (cond
      (types "definition") {:dispatch [:navigate-direct (str "@" (-> text c/remove-brackets c/definition-to-declaration))]}
      (types "declaration") {:dispatch [:navigate-direct (str "@" (-> text c/remove-brackets c/declaration-to-definition))]}
      (types "reference") {:dispatch [:navigate-direct (-> text c/remove-brackets)]}
      (types "link") {:open-window text})))

; opens up a new window with the URL provided
(rf/reg-fx :open-window
  (fn [url]
    (js/window.open url "_blank")))

;; finds the appropriate note and then calls :open-note for it to be
;; opened inside the client.
;;
;; if enable-copy-preview is set to true, then it will ask
;; the client to directly copy the state from the preview editor
;; into the actual editor, if it is possible ot open the aforementioned
;; note this way. (this will preserve information such as scroll pos)
(rf/reg-event-fx :request-open-note
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]} [_ name-or-note]]
    (cond
      (string? name-or-note)
      (let [name name-or-note
            existing-note (get (:notes db) name)]
        (if existing-note
          {:db db :dispatch [:request-open-note existing-note]}
          (let [a-new-note (n/new-note name time)]
            {:db       (assoc-in db [:notes name] a-new-note)
             :dispatch [:request-open-note a-new-note]})))

      (and (map? name-or-note) (= :note (:type name-or-note)))
      (let [note name-or-note]
        {:set-title (:name note)
         :set-hash (q/to-string (n/note-to-query note))
         :open-note [note]
         :db (-> db
                 (assoc :active-note-name (:name note))
                 (assoc :drag-start nil)
                 (assoc :history-cursor nil)
                 (assoc :history-direction nil)
                 (assoc :navigation nil)
                 (assoc :navigation-index nil)
                 (assoc-in [:notes (:name note)] note))}))))

; saves the content of a note if it has been changed
(rf/reg-event-fx :save-if-changed
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]} [_ name content]]
    (if (or (empty? name) (= content (get-in db [:notes name :content])))
      {:db db}
      {:db (-> db
               (assoc-in [:notes name :ntag] (find-ntag content))
               (assoc-in [:notes name :content] content)
               (assoc-in [:notes name :time-updated] time))
       :chsk-send
       (if-not (:read-only db) [:flo/save [name time content]])
       :eval-plugins-js
       (if (= name plugin-note-name) content)})))

(rf/reg-event-fx :run-plugin
  (fn [{:keys [db]} [_ plugin-name]]
    {:db db :run-plugin plugin-name}))

; updates the :time-changed attribute of the active note
; to become the current time
(rf/reg-event-fx :on-change
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]}]
    {:db (assoc-in db [:notes (:active-note-name db) :time-changed] time)}))

; called whenever the selection of the active note has been changed
(rf/reg-event-db :on-change-selection
  (fn [db [_ selection]]
    (assoc-in db [:notes (:active-note-name db) :selection] selection)))

; event handler for whenever the hash part of the URL has been changed
; is also called upon application startup
; @new-url: the complete url that is currently open
(rf/reg-event-fx :on-hash-change
  (fn [{:keys [db]} [_ new-url]]
    (let [hash-text (re-find c/regex-url-hash-part new-url)]
      (if-not hash-text {:db db}
        {:db db :dispatch [:navigate-direct hash-text true]}))))

; opens up an archive page which displays the same content
; as is currently shown in the editor, while also preserving
; the selection area
(rf/reg-event-fx :open-history-page
  [(rf/inject-cofx :time)]
  (fn [{:keys [db time]}]
    (let [timestamp (or (:history-cursor db) time)
          time-string (.format (js/moment timestamp) "YYYY-MM-DDTHH:mm:ss")
          note-name (:active-note-name db)
          path (str "/history?t=" time-string "#" note-name (n/note-selection-suffix (get (:notes db) note-name)))]
      {:db db :open-window path})))
