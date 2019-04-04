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

(defn goto-search
  [search]
  (if (empty? search)
    (quill/set-selection)
      (loop [s search]
        (or (>= 0 (count s))
            (quill/goto-substr (str "[" s "]"))
            (quill/goto-substr (str "[" s))
            (recur (splice-last s))))))

(add-watch state :auto-search
  (fn [_ _ _ {:keys [search]}]
    (println "search changed to" search)
    (when search (goto-search search))))

; when the user presses and releases the left shift key in quick succession
(defn on-hit-shift []
  (if-not (= "" (:search @state))
    (do (swap! state #(assoc % :search ""))
        (quill/disable-edit))
    (do (swap! state #(assoc % :search nil))
        (quill/enable-edit))))

(defn on-press-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (swap! state #(assoc % :last-shift-press (current-time-millis)))
    (swap! state #(assoc % :last-shift-press nil)))
  (when (:search @state)
    (if (= "Backspace" (:key event))
      (swap! state #(assoc % :search (splice-last (:search %)))))
    (when (re-matches #"^[A-Za-z0-9]$" (:key event))
      (swap! state #(assoc % :search (str (:search %) (str/upper-case (:key event))))))))

(defn on-release-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (let [delta (- (current-time-millis) (:last-shift-press @state 0))]
      (when (> 500 delta)
        (on-hit-shift)))))

(when-not js/window.initialized
  (add-event-listener "keydown" on-press-key)
  (add-event-listener "keyup" on-release-key))

(set! js/window.initialized true)

; called once received any items from chsk
(defn on-chsk-receive [item]
  (match (:event item)
    [:chsk/recv [:flo/load contents]] (quill/set-content contents)
    :else nil))

; initialize the socket connection
(def chsk-state (atom {:open? false}))
(let [{:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket! "/chsk" nil {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state)
  (go-loop []
    (let [item (<! ch-chsk)]
      (on-chsk-receive item))
    (recur)))

; sends a message to the server via socket to save the contents
(defn save-contents [contents]
  (if (:open? @chsk-state)
    (chsk-send! [:flo/save [file-id contents]])))

(defn detect-change []
  (let [content (quill/get-content)]
    (locking last-contents
      (when (= nil @quill/last-contents) (reset! quill/last-contents content))
      (when (not= content @quill/last-contents)
        (println "contents changed, saving...")
        (save-contents content)
        (reset! quill/last-contents content)))))

(js/setInterval detect-change 1000)

(defn on-js-reload [])
