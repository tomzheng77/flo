(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [cljs.core.async.macros :refer [go]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.functions :refer [json->clj current-time-millis splice-last add-event-listener]]
    [flo.client.quill :as quill]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs-http.client :as http]
    [cljs.core.async :as async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [clojure.string :as str]
    [cljsjs.jquery]
    [cljsjs.quill]
    [quill-image-resize-module]))

(enable-console-print!)

(def example-state
  {:last-shift-press 0                                      ; the time when the shift key was last pressed
   :search           "AA"                                   ; the active label being searched, nil means no search
   :content          {}})                                   ; the current contents of the editor

(defonce state
  (atom {:last-shift-press nil
         :search           nil
         :select           nil
         :content          nil}))

(defonce configuration
  (->> "init"
       (.getElementById js/document)
       (.-innerHTML)
       (read-string)))

(def file-id (:file-id configuration))
(def initial-content (:content configuration))
(quill/new-instance)
(quill/set-content initial-content)

(println "file:" file-id)
(println "initial content:" initial-content)

(defn find-all [text substr]
  (loop [start-index 0 output []]
    (let [index (str/index-of text substr start-index)]
      (if-not index
        output
        (recur (inc start-index) (conj output {:index index :length (count substr)}))))))

(defn intersects [occur-a occur-b]
  (let [start-a (:index occur-a)
        start-b (:index occur-b)
        end-a (+ start-a (:length occur-a))
        end-b (+ start-b (:length occur-b))]
    (not (or (<= end-b start-a)
             (<= end-a start-b)))))

(defn remove-dups [occurs]
  (loop [seen [] remain occurs]
    (if (empty? remain)
      seen
      (if (some #(intersects % (first remain)) seen)
        (recur seen (next remain))
        (recur (conj seen (first remain)) (next remain))))))

(defn navigate [search select]
  (if (empty? search)
    (quill/set-selection)
    (let [text (quill/get-text)]
      (loop [s search]
        (if (not-empty s)
          (let [occur      (concat (find-all text (str "[" s "]"))
                                   (find-all text (str "[" s "=]"))
                                   (find-all text (str "[" s)))
                occur-uniq (remove-dups occur)
                target     (and (not-empty occur-uniq)
                                (nth occur-uniq (mod select (count occur-uniq))))]
            (if-not target
              (recur (splice-last s))
              (quill/goto (:index target) (:length target)))))))))

(add-watch state :auto-search
  (fn [_ _ old new]
    (println new)
    (if (or (not= (:search old) (:search new))
            (not= (:select old) (:select new)))
      (if (:search new)
        (navigate (:search new) (:select new 0))))))

(defn on-hit-shift []
  (if-not (= "" (:search @state))
    (do (swap! state #(-> % (assoc :search "") (assoc :select 0)))
        (quill/disable-edit))
    (do (swap! state #(-> % (assoc :search nil) (assoc :select 0)))
        (quill/enable-edit)
        (quill/focus)
        (quill/set-selection-before))))

(defn on-press-key
  [event]
  (println event)
  (if (= "ShiftLeft" (:code event))
    (swap! state #(assoc % :last-shift-press (current-time-millis)))
    (swap! state #(assoc % :last-shift-press nil)))
  (when (= "Escape" (:code event))
    (swap! state #(-> % (assoc :search nil) (assoc :select 0)))
    (quill/enable-edit)
    (quill/focus)
    (quill/set-selection-before))
  (when (:search @state)
    (when (= "Tab" (:key event))
      (swap! state #(-> % (assoc :select (inc (:select %)))))
      (.preventDefault (:original event)))
    (when (= "Backspace" (:key event))
      (swap! state #(-> % (assoc :search (splice-last (:search %)))
                          (assoc :select 0))))
    (when (re-matches #"^[A-Za-z0-9]$" (:key event))
      (swap! state #(-> % (assoc :search (str (:search %) (str/upper-case (:key event))))
                          (assoc :select 0))))))

(defn on-release-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (let [delta (- (current-time-millis) (:last-shift-press @state 0))]
      (when (> 500 delta)
        (on-hit-shift)))))

; this initializer will be called once per document
(defn initialize-once []
  (add-event-listener "keydown" on-press-key)
  (add-event-listener "keyup" on-release-key))

(let [{:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket! "/chsk" nil {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(defn save-content [content]
  (if (:open? @chsk-state)
    (chsk-send! [:flo/save [file-id content]])))

(def last-save (atom nil))
(defn detect-change []
  (let [content (quill/get-content)]
    (locking last-save
      (when (nil? @last-save) (reset! last-save content))
      (when (not= content @last-save)
        (save-content content)
        (reset! last-save content)))))

(js/setInterval detect-change 1000)

(defn on-js-reload [])
(when-not js/window.initialized (initialize-once))
(set! js/window.initialized true)
