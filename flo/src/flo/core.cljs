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
      (sente/make-channel-socket! "/chsk" nil {:type :auto :host "localhost:9050"})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))


(defn start-loop []
  (js/setTimeout
    (fn []
      (let [string (.stringify js/JSON (.getContents js/quill))]
        (println (js->clj (.parse js/JSON string)))
        (.setContents js/quill (.parse js/JSON string)))
      (start-loop))
    1000))

(start-loop)

(add-watch chsk-state "watch"
           (fn [key ref old new]
             (if (:open? new)
               (chsk-send! [:flo/hello "hello"]))))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
