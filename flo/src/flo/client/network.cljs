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
  (def chsk-state state))

(go-loop []
  (let [item (<! ch-chsk)]
    (match (:event item)
      [:chsk/recv [:flo/history [fid timestamp note]]] (rf/dispatch [:recv-history timestamp note])
      :else nil))
  (recur))

(rf/reg-fx :save
  (fn [content]
    (when (:open? @chsk-state)
      (rf/dispatch [:new-save (current-time-millis)])
      (chsk-send! [:flo/save [@(rf/subscribe [:file-id]) content]]))))

(rf/reg-event-fx :edit
  (fn [{:keys [db]} [_ content]]
    (if (= content (:content db))
      {:db db}
      {:save content :db (assoc db :last-save content)})))

(add-watch-db :drag-changed [:drag-timestamp]
  (fn [_ _ _ timestamp]
    (chsk-send! [:flo/seek [@(rf/subscribe [:file-id]) (js/Math.round timestamp)]])))
