(ns flo.client.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [cljs.core.async.macros :refer [go]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.quill :as quill]
    [flo.client.functions :refer [json->clj current-time-millis splice-last add-event-listener find-all
                                  intersects remove-overlaps]]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [taoensso.sente.packers.transit :as transit]
    [clojure.string :as str]
    [cljsjs.jquery]
    [cljsjs.quill]
    [quill-image-resize-module]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]))

(enable-console-print!)
(defonce configuration
   (->> "init"
        (.getElementById js/document)
        (.-innerHTML)
        (b64/decodeString)
        (read-string)))

(def example-state
  {:last-shift-press 0                                      ; the time when the shift key was last pressed
   :search           "AA"                                   ; the active label being searched, nil means no search
   :content          {}})                                   ; the current contents of the editor

(defonce state
  (r/atom {:last-shift-press nil
           :search           nil
           :select           nil
           :content          nil}))

(def status (r/atom nil))

; https://coolors.co/3da1d2-dcf8fe-6da6cc-3aa0d5-bde7f3
(defn app []
  [:div#app-inner
   [:div#editor]
   [:div#status {:style {:height           "40px"
                         :background-color "#3DA1D2"
                         :line-height      "40px"
                         :color            "#FFF"
                         :font-family      "Monospace"
                         :text-indent      "10px"
                         :flex-grow        "0"
                         :flex-shrink      "0"}} @status]])

(r/render [app] (js/document.getElementById "app"))

(def file-id (:file-id configuration))
(def initial-content (:content configuration))
(quill/new-instance)
(quill/set-content initial-content)

(println "file:" file-id)
(println "initial content:" initial-content)

(defn navigate [search select]
  (if (empty? search)
    (quill/set-selection)
    (let [text (quill/get-text)]
      (loop [s search]
        (if (not-empty s)
          (let [occur      (concat (find-all text (str "[" s "]"))
                                   (find-all text (str "[" s "=]"))
                                   (find-all text (str "[=" s "]"))
                                   (find-all text (str "[=" s "=]"))
                                   (find-all text (str "[" s)))
                occur-uniq (sort-by :start (remove-overlaps occur))
                target     (and (not-empty occur-uniq)
                                (nth occur-uniq (mod select (count occur-uniq))))]
            (if-not target
              (recur (splice-last s))
              (quill/goto (:index target) (:length target)))))))))

(add-watch state :auto-search
  (fn [_ _ old new]
    (reset! status (str "Search: " (pr-str (:search new))))
    (if (or (not= (:search old) (:search new))
            (not= (:select old) (:select new)))
      (if (:search new)
        (navigate (:search new) (:select new 0))))))

(reset! state @state)

(defn on-hit-shift []
  (if-not (= "" (:search @state))
    (do (swap! state #(-> % (assoc :search "") (assoc :select 0)))
        (quill/disable-edit))
    (do (swap! state #(-> % (assoc :search nil) (assoc :select 0)))
        (quill/enable-edit)
        (quill/focus)
        (quill/set-cursor-at-selection))))

(defn on-press-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (swap! state #(assoc % :last-shift-press (current-time-millis)))
    (swap! state #(assoc % :last-shift-press nil)))
  (when (= "Escape" (:code event))
    (swap! state #(-> % (assoc :search nil) (assoc :select 0)))
    (quill/enable-edit)
    (quill/focus)
    (quill/set-cursor-at-selection))
  (when (:search @state)
    (when (= "Tab" (:key event))
      (swap! state #(-> % (assoc :select (inc (:select %)))))
      (.preventDefault (:original event)))
    (when (= "Backspace" (:key event))
      (swap! state #(-> % (assoc :search (splice-last (:search %)))
                          (assoc :select 0))))
    (when (re-matches #"^[A-Za-z0-9]$" (:key event))
      (swap! state #(-> % (assoc :search (str (:search %) (str/upper-case (:key event))))
                          (assoc :select 0)))))
  (when (and (:ctrl-key event) (= "h" (:key event)))
    (quill/highlight-tags)
    (.preventDefault (:original event))))

(defn on-release-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (let [delta (- (current-time-millis) (:last-shift-press @state 0))]
      (when (> 200 delta)
        (on-hit-shift)))))

; this initializer will be called once per document
(defn initialize-once []
  (add-event-listener "keydown" on-press-key)
  (add-event-listener "keyup" on-release-key))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" nil
        {:type :auto :packer (transit/get-transit-packer)})]
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
