(ns flo.client.ui.history-bar
  (:require
    [flo.client.functions :refer [to-clj-event]]
    [re-frame.core :as rf]
    [reagent.core :as r]))

(defn- history-limit [label limit-ms]
  (let [hover? (r/atom false)
        press? (r/atom false)]
    (fn []
      [:div {:style {:width "24px"
                     :height "24px"
                     :font-size "11px"
                     :line-height "24px"
                     :text-align "center"
                     :color "rgba(255, 255, 255, 0.5)"
                     :user-select "none"
                     :font-family "Monospace"
                     :cursor "pointer"
                     :border-right "1px solid rgba(255, 255, 255, 0.3)"
                     :background-color
                     (cond
                       (or @press? (= limit-ms @(rf/subscribe [:history-limit]))) "rgba(0, 0, 0, 0.3)"
                       @hover? "rgba(0, 0, 0, 0.1)")}
             :on-mouse-over #(reset! hover? true)
             :on-mouse-out #(do (reset! hover? false) (reset! press? false))
             :on-mouse-down #(reset! press? true)
             :on-mouse-up #(reset! press? false)
             :on-click #(rf/dispatch [:set-history-limit limit-ms])} label])))


(defn- toggle-button [label event subscription]
  (let [hover? (r/atom false)
        press? (r/atom false)]
    (fn []
      [:div {:style {:height "24px"
                     :font-size "11px"
                     :line-height "24px"
                     :text-align "center"
                     :color "rgba(255, 255, 255, 0.5)"
                     :user-select "none"
                     :font-family "Monospace"
                     :cursor "pointer"
                     :border-right "1px solid rgba(255, 255, 255, 0.3)"
                     :padding-left 8
                     :padding-right 8
                     :background-color
                     (cond
                       (or @press? @(rf/subscribe [subscription])) "rgba(0, 0, 0, 0.3)"
                       @hover? "rgba(0, 0, 0, 0.1)")}
             :on-mouse-over #(reset! hover? true)
             :on-mouse-out #(do (reset! hover? false) (reset! press? false))
             :on-mouse-down #(reset! press? true)
             :on-mouse-up #(reset! press? false)
             :on-click #(rf/dispatch [event])} label])))


(defn- plugin-button [label]
  (let [hover? (r/atom false)
        press? (r/atom false)]
    (fn []
      [:div {:style {:height "24px"
                     :font-size "11px"
                     :line-height "24px"
                     :text-align "center"
                     :color "rgba(255, 255, 255, 0.5)"
                     :user-select "none"
                     :font-family "Monospace"
                     :cursor "pointer"
                     :border-right "1px solid rgba(255, 255, 255, 0.3)"
                     :padding-left 8
                     :padding-right 8
                     :background-color
                     (cond
                       @press? "rgba(0, 0, 0, 0.3)"
                       @hover? "rgba(0, 0, 0, 0.1)")}
             :on-mouse-over #(reset! hover? true)
             :on-mouse-out #(do (reset! hover? false) (reset! press? false))
             :on-mouse-down #(reset! press? true)
             :on-mouse-up #(reset! press? false)
             :on-click #(rf/dispatch [:run-plugin label])} label])))


(defn- on-drag-start [event drag-btn-x]
  (let [clj-event (to-clj-event event)]
    (rf/dispatch [:start-drag {:mouse-x (:mouse-x clj-event) :btn-x drag-btn-x}])))


(defn- history-button []
  (let [timestamp (or @(rf/subscribe [:history-cursor]) @(rf/subscribe [:active-time-updated]))
        drag-btn-x @(rf/subscribe [:history-button-x])]
    [:div {:style {:position         "absolute"
                   :height           "20px"
                   :text-indent      "0"
                   :text-align       "center"
                   :background-color "#9e2023"
                   :font-family      "Monospace"
                   :padding-top      "2px"
                   :padding-bottom   "2px"
                   :color            "#FFF"
                   :cursor           "pointer"
                   :user-select      "none"
                   :line-height      "10px"
                   :font-size        8
                   :width            @(rf/subscribe [:drag-btn-width])
                   :left             drag-btn-x}
           :on-touch-start #(on-drag-start % drag-btn-x)
           :on-mouse-down #(on-drag-start % drag-btn-x)}
     (.format (js/moment timestamp) "YYYY-MM-DD") [:br]
     (.format (js/moment timestamp) "h:mm:ss a")]))


(defn- status-display []
  [:div {:style {:height           "24px"
                 :text-indent      "10px"
                 :font-size        "11px"
                 :line-height      "24px"
                 :color            "rgba(255, 255, 255, 0.5)"
                 :font-family      "Monospace"}}
   @(rf/subscribe [:status-text])])


(defn component []
  [:div {:style {:height           "24px"
                 :background-color "#9e4446"
                 :flex-grow        "0"
                 :flex-shrink      "0"
                 :overflow         "hidden"
                 :display          "flex"
                 :align-items      "center"}}
   [history-limit "5m" (* 1000 60 5)]
   [history-limit "H" (* 1000 60 60)]
   [history-limit "D" (* 1000 60 60 24)]
   [history-limit "W" (* 1000 60 60 24 7)]
   [history-limit "M" (* 1000 60 60 24 30)]
   [history-limit "Y" (* 1000 60 60 24 365)]
   [history-limit "A" (* 1000 60 60 24 10000)]
   [toggle-button "Table" :toggle-table-on :table-on]
   [toggle-button "Autosave" :toggle-autosave :autosave]
   [plugin-button "F1"]
   [plugin-button "F2"]
   [plugin-button "F3"]
   [status-display]
   ; todo: add realtime switch
   [history-button]])
