(ns flo.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [cljs.core.async.macros :refer [go]]
    [cljs.core.match :refer [match]])
  (:require
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs-http.client :as http]
    [cljs.core.async :as async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]))

(enable-console-print!)

(println "This text is printed from src/flo/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

; called once received any items from chsk
(defn on-chsk-receive [item]
  (match item
    [:chsk-recv [body]] (println body)))

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

; contents since it was last inspected
; get contents from quill
; set contents of quill
(def last-contents (atom nil))
(defn get-contents [] (js->clj (.parse js/JSON (.stringify js/JSON (.getContents js/quill)))))
(defn set-contents [contents]
  (.setContents (clj->js contents)))

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
