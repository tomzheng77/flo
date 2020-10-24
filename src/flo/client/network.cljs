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

;; creates the sente connection for two-way communication
;; between this client and the server
;;
;; once the socket is established, a list of global objects are declared, notably:
;; - ch-chsk: channel for receiving events from the server
;; - chsk-send!: sends a packet to the server
;; - chsk-state: stores whether the connection is open or not
(let [{:keys [chsk ch-recv send-fn state]}
  (sente/make-channel-socket! "/chsk" nil {:type :auto :packer (transit/get-transit-packer)})]
  (def chsk chsk)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state)
  (go-loop []
    (let [item (<! ch-chsk)]
      (rf/dispatch [:chsk-event (:event item)]))
    (recur)))

;; sends an arbitrary message to the backend via sente
;; event should be [${type}, ${contents}] where ${type} should
;; be a tag and ${contents} should be a list of arguments
;;
;; currently the supported messages are:
;; [:flo/save [name time content]]
;; [:flo/seek [name time]]
(rf/reg-fx :chsk-send
  (fn [event]
    (when-not (:open? @chsk-state)
      (js/alert (str "failed to send message: " (pr-str event))))
    (when (:open? @chsk-state)
      (chsk-send! event))))
