(ns flo.client.client-excel
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.ace.ace :as ace]
    [flo.client.ace.ace-clickables]
    [flo.client.ace.ace-colors]
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all intersects remove-overlaps to-clj-event]]
    [flo.client.store :refer [add-watches-db add-watch-db db active-history]]
    [flo.client.network]
    [flo.client.view :refer [navigation search-bar history-bar]]
    [flo.client.constants :as c]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [cljs.core.async :refer [<! >! put! chan]]
    [taoensso.sente :as sente :refer [cb-success?]]
    [taoensso.sente.packers.transit :as transit]
    [clojure.string :as str]
    [clojure.data.avl :as avl]
    [cljsjs.moment]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [clojure.set :as set]
    [diff :as diff]))

(def source (r/atom 
  (into []
    (for [row (range 200)]
      (into []
        (for [col (range 15)]
          (str row " " col)))))))

(defn on-input [i j event]
  (let [textarea (.-currentTarget event)
        value (.-value textarea)]
    (console-log value)
    (console-log (.-scrollHeight textarea))
    ))

(defn view []
  [:div#container-excel "view div"
    [:table {:style {:table-layout :fixed}}
      [:colgroup
        (map-indexed (fn [i _] ^{:key i} [:col {:style {:width 100}}]) (range 15))]
      (doall (map-indexed (fn [i row] ^{:key i} [:tr
        (doall (map-indexed (fn [j cell] ^{:key [i j]}
          [:td {:style {:height 36}}
            [:textarea.cell {:style {:width "100%"} :default-value cell
                             :on-change #(on-input i j %)}]]) row))]) @source))]])

; the possibility of implementing very lightweight spreadsheet
; using contenteditable and terminal emulator
; directly edit the content of cells
; set color using <#F00> at the end of cell text
; simply 2D array of strings and nothing else
; first row can set width of each column <110px>

; JavaScript API:
; add_column(letter): creates a column at or up to the specified index, shifts existing if any
; add_row(index): creates a row at or up to the specified index, shifts existing if any
; copy(src, dst): src can be cell/row/column/section, dst can be cell/row/column/section
; move(src, dst): same as copy except does not preserve the original section
; sort(letter, method): sorts based on the specified column, uses the method specified (number/datetime/string)
; is tempting to use a simple binding with reagent
; should support up to 10,000 cells
