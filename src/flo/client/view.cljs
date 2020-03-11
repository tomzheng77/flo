(ns flo.client.view
  (:require
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
    [re-frame.core :as rf]
    [reagent.core :as r]))

(defn navigation-row [note]
  (let [focus? (r/atom false)]
    (fn []
      [:div {:style {:width "auto" :height 24
                     :font-size 15
                     :text-indent 7
                     :padding-right 7
                     :line-height "24px"
                     :user-select "none"
                     :background-color (if (or @focus? (:focus note)) "#c7cbd1")
                     :cursor "pointer"
                     :display "flex"
                     :flex-direction "row"}
             :on-mouse-over #(reset! focus? true)
             :on-mouse-out #(reset! focus? false)
             :on-click #(rf/dispatch [:open-note note false])}
       (if (:ntag note) [:div {:style {:background-color "rgba(0,0,0,0.1)"
                                       :text-align "center"
                                       :font-family "Monospace"
                                       :font-size 12
                                       :text-indent 0
                                       :padding-left 10
                                       :padding-right 10}} (:ntag note)])
       [:div (:name note)]
       [:div {:style {:flex-grow 1}}]
       [:div {:style {:color "#777"}} (count (:content note)) " chars"]
       [:div {:style {:color "#777"}} (.format (js/moment (:time-updated note)) "MM-DD hh:mm:ss")]
       ])))


(defn navigation []
  [:div#navigation-outer
   {:on-click #(rf/dispatch [:navigation-input nil])
    :style {:position "absolute"
            :top      0
            :bottom   0
            :left     0
            :right    0
            :z-index  10}}
   [:div#navigation
    {:style {:width 600
             :margin-left "auto"
             :margin-right "auto"
             :font-family "Monospace"
             :padding 4
             :background-color "#ebedef"}}
    [:div {:style {:height 30 :background-color "white"}}
     [:input {:style {:border "none"
                      :line-height "30px"
                      :width "100%"
                      :height "100%"
                      :padding 0
                      :text-indent 7}
              :auto-focus true
              :value @(rf/subscribe [:navigation])
              :on-change #(rf/dispatch [:navigation-input (-> % .-target .-value)])}]]
    [:div {:style {:height 4}}]
    [:div {:style {:max-height 504 :overflow-y "scroll"}}
     (for [note @(rf/subscribe [:navigation-list])]
       ^{:key [(:name note) (:focus note)]}
       [navigation-row note])]]])


(defn history-limit [label limit-ms]
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


(defn on-drag-start [event drag-btn-x]
  (let [clj-event (to-clj-event event)]
    (rf/dispatch [:start-drag {:mouse-x (:mouse-x clj-event) :btn-x drag-btn-x}])))


(defn history-button []
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


(defn status-display []
  [:div {:style {:height           "24px"
                 :text-indent      "10px"
                 :font-size        "11px"
                 :line-height      "24px"
                 :color            "rgba(255, 255, 255, 0.5)"
                 :font-family      "Monospace"}}
   @(rf/subscribe [:status-text])])

(defn history-bar []
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
   [status-display]
   ; todo: add realtime switch
   [history-button]])


(defn search-bar []
  [:div {:style {:height           "24px"
                 :background-color "#3DA1D2"
                 :line-height      "24px"
                 :color            "#FFF"
                 :font-family      "Monospace"
                 :font-size        "11px"
                 :text-indent      "10px"
                 :flex-grow        "0"
                 :flex-shrink      "0"}}
   (str "Search: " (pr-str @(rf/subscribe [:search])))])
