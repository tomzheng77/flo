(ns flo.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]])
  (:require
    [cljs.core.async :as async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]))

(enable-console-print!)

(println "This text is printed from src/flo/core.cljs. Go ahead and edit it and see reloading in action.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" nil {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))

(def last-contents (atom nil))
(defn get-contents [] (js->clj (.parse js/JSON (.stringify js/JSON (.getContents js/quill)))))
(defn set-contents [contents]
  (.setContents (clj->js contents)))

(go-loop []
  (let [item (<! ch-chsk)]
    (println "client received" item))
  (recur))

(defn save-contents [contents]
  (if (:open? @chsk-state)
    (chsk-send! [:flo/save contents])))

(defn on-enter-frame []
  (let [contents (get-contents)]
    (locking last-contents
      (when (= nil @last-contents) (reset! last-contents contents))
      (when (not= contents @last-contents)
        (println "contents changed, saving...")
        (save-contents contents)
        (reset! last-contents contents)))))

(js/setInterval on-enter-frame 1000)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
