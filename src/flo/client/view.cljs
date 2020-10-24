(ns flo.client.view
  (:require
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all to-clj-event]]
    [re-frame.core :as rf]
    [reagent.core :as r]))

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
