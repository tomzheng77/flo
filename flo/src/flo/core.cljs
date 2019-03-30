(ns flo.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [cljs.core.async.macros :refer [go]]
    [flo.macros :refer [console-log]])
  (:require
    [cljsjs.quill]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs-http.client :as http]
    [cljs.core.async :as async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [clojure.string :as str]))

(enable-console-print!)

; converts one or more arguments to JSON then clojure
; accepts same options as js->clj
(defn json->clj [x & opts]
  (apply js->clj (concat [(.parse js/JSON (.stringify js/JSON x))] opts)))

(defn compose-delta [old-delta new-delta]
  (.compose old-delta new-delta))

; track the contents of quill using an atom
(def contents (atom {}))

; create the quill editor instance
(def quill
  (new js/Quill "#editor"
       (clj->js {"modules" {"toolbar" "#toolbar"}
                 "theme" "snow"})))

(.on quill "text-change"
     (fn [new-delta old-delta source]
       (reset! contents (json->clj (compose-delta old-delta new-delta)))))

; contents since it was last inspected
; get contents from quill
; set contents of quill
(def last-contents (atom nil))
(defn enable-edit [] (.enable quill))
(defn disable-edit [] (.disable quill))
(defn get-text [] (.getText quill))
(defn get-contents [] (json->clj (.getContents quill)))
(defn set-contents [contents]
  (.setContents quill (clj->js contents)))
(defn set-selection
  ([] (.setSelection quill nil))
  ([index length]
   (.setSelection quill index length)))
(defn get-bounds [index length]
  (js->clj (.getBounds quill index length)))

(def editor (aget (.. (.getElementById js/document "editor") -children) 0))
(defn scroll-by [x y]
  (.scrollBy editor x y))

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
      (set-selection index length)
      (let [bounds (get-bounds index length)]
        (scroll-by (get bounds "left")
                   (get bounds "top")))
      index)))

(defn go-to-tag
  [search]
  (if (empty? search)
    (set-selection)
    (let [text (get-text)]
      (loop [s search]
        (or (>= 0 (count s))
            (go-to-substr text (str "[" s "]"))
            (go-to-substr text (str "[" s))
            (recur (splice-last s)))))))

(defn on-keydown
  [event]
  (if (= "ShiftLeft" (:code event))
    (reset! shift-press-time (current-time-millis))
    (do (reset! shift-press-time 0)
        (when @search-active
          (if (= "Backspace" (:key event))
            (do
              (swap! search splice-last)
              (go-to-tag @search))
            (when (re-matches #"^[A-Za-z0-9]$" (:key event))
              (swap! search #(str % (str/upper-case (:key event))))
              (go-to-tag @search)))))))

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
              (disable-edit))
          (do (reset! search-active false)
              (reset! search "")
              (enable-edit)))))))

(defonce add-listeners
  (do (add-event-listener "keydown" on-keydown)
      (add-event-listener "keyup" on-keyup)))

;; define your app data so that it doesn't get over-written on reload
(defonce app-state (atom {:text "Hello world!"}))

; called once received any items from chsk
(defn on-chsk-receive [item]
  (match (:event item)
    [:chsk/recv [:flo/load contents]] (set-contents contents)
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
  (let [contents (get-contents)]
    (locking last-contents
      (when (= nil @last-contents) (reset! last-contents contents))
      (when (not= contents @last-contents)
        (println "contents changed, saving...")
        (save-contents contents)
        (reset! last-contents contents)))))

(js/setInterval detect-change 1000)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
