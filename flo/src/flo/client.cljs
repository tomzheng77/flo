(ns flo.client
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [cljs.core.async.macros :refer [go]]
    [flo.macros :refer [console-log]])
  (:require
    [flo.functions :refer [json->clj current-time-millis splice-last
                           add-event-listener remove-event-listener]]
    [flo.quill :as quill]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs-http.client :as http]
    [cljs.core.async :as async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [clojure.string :as str]))

(enable-console-print!)

(def example-state
  {:last-shift-press 0                                      ; the time when the shift key was last pressed
   :search           "AA"                                   ; the active label being searched, nil means no search
   :content          {}})                                   ; the current contents of the editor

(defonce state (atom {:last-shift-press nil
                      :search           nil
                      :content          nil}))

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
  (if-not (:search @state)
    (do (swap! state #(assoc % :search ""))
        (quill/disable-edit))
    (do (swap! state #(assoc % :search nil))
        (quill/enable-edit))))

(defn on-press-key
  [event]
  (if (= "ShiftLeft" (:code event))
    (swap! state #(assoc % :last-shift-press (current-time-millis)))
    (swap! state #(assoc % :last-shift-press nil)))
  (if (:search @state)
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

(remove-event-listener "keydown" on-press-key)
(remove-event-listener "keyup" on-release-key)
(add-event-listener "keydown" on-press-key)
(add-event-listener "keyup" on-release-key)

; called once received any items from chsk
(defn on-chsk-receive [item]
  (match (:event item)
    [:chsk/recv [:flo/load contents]] (quill/set-contents contents)
    :else nil))

; initialize the socket connection
(def chsk-state (atom {:open? false}))
(go (let [csrf-token (-> (<! (http/get "/login"))
                         (:body)
                         (read-string)
                         (:csrf-token))]

      (let [{:keys [chsk ch-recv send-fn state]}
            (sente/make-channel-socket! "/chsk" nil {:type :auto})]
        (def chsk chsk)
        (def ch-chsk ch-recv)
        (def chsk-send! send-fn)
        (def chsk-state state)
        (go-loop []
          (let [item (<! ch-chsk)]
            (on-chsk-receive item))
          (recur)))))

; sends a message to the server via socket to save the contents
(defn save-contents [contents]
  (if (:open? @chsk-state)
    (chsk-send! [:flo/save contents])))

(defn detect-change []
  (let [contents (quill/get-contents)]
    (locking last-contents
      (when (= nil @quill/last-contents) (reset! quill/last-contents contents))
      (when (not= contents @quill/last-contents)
        (println "contents changed, saving...")
        (save-contents contents)
        (reset! quill/last-contents contents)))))

(js/setInterval detect-change 1000)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
