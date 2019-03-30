(ns flo.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [cljs.core.async.macros :refer [go]]
    [flo.macros :refer [console-log]])
  (:require
    [flo.functions :refer [json->clj]]
    [cljsjs.quill]
    [flo.quill :as quill]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs-http.client :as http]
    [cljs.core.async :as async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [clojure.string :as str]))

(enable-console-print!)

(defn add-event-listener [type listener]
  (js/addEventListener type
    (fn [event]
      (let [clj-event {:code (. event -code) :key (. event -key)}]
        (listener clj-event)))))

(defn splice-last [str]
  (subs str 0 (dec (count str))))

(def shift-press-time (atom 0))
(def search-active (atom false))
(def search (atom ""))

(defn current-time-millis [] (.getTime (new js/Date)))

(defn go-to-substr
  [text substr]
  (let [index (str/index-of text substr)
        length (count substr)]
    (when index
      (quill/set-selection index length)
      (let [bounds (quill/get-bounds index length)]
        (quill/scroll-by (get bounds "left")
                   (get bounds "top")))
      index)))

(defn go-to-tag
  [search]
  (if (empty? search)
    (quill/set-selection)
    (let [text (quill/get-text)]
      (loop [s search]
        (or (>= 0 (count s))
            (go-to-substr text (str "[" s "]"))
            (go-to-substr text (str "[" s))
            (recur (splice-last s)))))))

(add-watch search :auto-search
  (fn [key ref old new]
    (println "search changed to" new)
    (go-to-tag new)))

(defn on-keydown
  [event]
  (if (= "ShiftLeft" (:code event))
    (reset! shift-press-time (current-time-millis))
    (do (reset! shift-press-time 0)
        (when @search-active
          (if (= "Backspace" (:key event))
            (swap! search splice-last)
            (when (re-matches #"^[A-Za-z0-9]$" (:key event))
              (swap! search #(str % (str/upper-case (:key event))))))))))

(defn on-keyup
  [event]
  (if (= "ShiftLeft" (:code event))
    (let [now-time (current-time-millis)
          delta (- now-time @shift-press-time)]
      (when (> 500 delta)
        (if-not @search-active
          (do (println "activate search")
              (reset! search-active true)
              (reset! search "")
              (quill/disable-edit))
          (do (reset! search-active false)
              (reset! search "")
              (quill/enable-edit)))))))

(defonce add-listeners
  (do (add-event-listener "keydown" on-keydown)
      (add-event-listener "keyup" on-keyup)))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Hello world!"}))

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
