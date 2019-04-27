(ns flo.client.network
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as transit]
            [re-frame.core :as rf]
            [flo.client.store :refer [add-watches-db add-watch-db db]]
            [flo.client.functions :refer [json->clj current-time-millis]]
            [cljs.core.match :refer-macros [match]]))

(let [{:keys [chsk ch-recv send-fn state]}
  (sente/make-channel-socket! "/chsk" nil {:type :auto :packer (transit/get-transit-packer)})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state)
  (go-loop []
    (let [item (<! ch-chsk)]
      (println item)
      (rf/dispatch [:chsk-event (:event item)]))
    (recur)))

(rf/reg-fx :chsk-send
  (fn [event]
    (when (:open? @chsk-state)
      (chsk-send! event))))
