(ns flo.client.view
  (:require
    [re-frame.core :as rf]))

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
