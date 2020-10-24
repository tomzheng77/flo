(ns flo.client.ui.navigation
  (:require
    [re-frame.core :as rf]
    [reagent.core :as r]))

(defn- navigation-row [note]
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
             :on-click #(rf/dispatch [:request-open-note note])}
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

(defn component []
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
