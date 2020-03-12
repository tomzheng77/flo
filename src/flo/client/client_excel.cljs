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

; an atom
; of vector of atoms (rows)
; of vector of atoms (cols)
; of strings (cells)
(def source (r/atom
  (into []
    (for [row (range 20)]
      (r/atom
        (into []
        (for [col (range 6)]
          (r/atom {
            :c "#FFF"
            :bgc "#272822"
            :s (str row "-" col)
            :h 1
          }))))))))

(def line-height 20)

(defn on-input [i j cell-atom textarea]
  (let [td (.-parentNode textarea)
        value (.-value textarea)
        height (.-scrollHeight textarea)
        bgc (atom (last (first (re-seq #"<B:(#[A-F0-9]{3}|#[A-F0-9]{6})>" value))))
        c (atom (last (first (re-seq #"<C:(#[A-F0-9]{3}|#[A-F0-9]{6})>" value))))
        r? (not (nil? (re-seq #"<R>" value)))
        g? (not (nil? (re-seq #"<G>" value)))
        b? (not (nil? (re-seq #"<B>" value)))]
    (console-log textarea td (.-clientWidth textarea) (.-clientHeight textarea) height)
    (when (and (not @bgc) (not @c))
      (cond
        r? (do (reset! bgc "#F00") (reset! c "#FFF"))
        g? (do (reset! bgc "#0B0") (reset! c "#FFF"))
        b? (do (reset! bgc "#069") (reset! c "#FFF"))))
    (when (not @bgc) (reset! bgc "#272822"))
    (when (not @c) (reset! c "#FFF"))
    (let [old-atom-val @cell-atom
          new-atom-val (-> old-atom-val
          (assoc :s value)
          (assoc :h (js/Math.round (/ height line-height)))
          (assoc :bgc @bgc)
          (assoc :c @c))]
      (reset! cell-atom new-atom-val)
      (set! (.-height (.-style td)) (str (* (new-atom-val :h) line-height) "px"))
      (set! (.-backgroundColor (.-style td)) (new-atom-val :bgc))
      (set! (.-height (.-style textarea)) (str (* (new-atom-val :h) line-height) "px"))
      (set! (.-color (.-style textarea)) (new-atom-val :c)))))


(defn width-from-cell-atom-v [cell-atom-v]
  (let [value (cell-atom-v :s)
        width (last (first (re-seq #"<W:([0-9]+)>" value)))]
    (max 15 (if width (read-string width) 100))))


(defn col [cell-atom]
  [:col {:style {:width (width-from-cell-atom-v @cell-atom) :min-width (width-from-cell-atom-v @cell-atom)}}])


(defn colgroup [row-atom]
  [:colgroup [:col {:style {:width 50}}] (map-indexed (fn [i cell-atom] ^{:key i} [col cell-atom]) @row-atom)])


(defn cell-view [i j cell-atom]
  (fn []
    (r/create-class {
      :reagent-render (fn [i j cell-atom]
      [:td {:style {}}
       [:textarea.cell {:style {
        :width "100%"}
        :value (@cell-atom :s)
        :on-change #(on-input i j cell-atom (.-currentTarget %))}]])

      :component-did-mount
      (fn [comp]
        (let [td (r/dom-node comp)
              textarea (aget (.-childNodes td) 0)]
          (on-input i j cell-atom textarea)))

      :component-did-update
      (fn [comp]
        (let [td (r/dom-node comp)
              textarea (aget (.-childNodes td) 0)]
          (on-input i j cell-atom textarea)))})))


(defn row-view [i row-atom]
  [:tr [:td {:style {:text-align "center" :background-color "#FFF" :color "#272822"}} (+ i 1)]
    (doall (map-indexed (fn [j cell-atom] ^{:key [i j]}
      [cell-view i j cell-atom]) @row-atom))])


(defn width-sum []
  (if (> (count @source) 0)
    (let [first-row-atom (first @source)]
      (apply +
        (for [cell-atom @first-row-atom]
          (width-from-cell-atom-v @cell-atom))))))


(defn index-to-label [index]
  (if (string? index)
    (index-to-label (read-string index))
    (if (or (not (int? index)) (> 0 index)) "A"
      (if (> 26 index) (str (char (+ 65 index)))
        (str (index-to-label (quot index 26))
             (index-to-label (mod index 26)))))))


(defn view []
  [:div#container-excel {:style {:flex-grow 1 :overflow :scroll}}
    [:table {:style {:table-layout :fixed :width (width-sum)}}
      (if (> (count @source) 0)
        (let [first-row-atom (first @source)]
          [colgroup first-row-atom]))
      [:tbody
        (if (> (count @source) 0)
          (let [first-row-atom (first @source)]
            [:tr [:td {:style {:text-align "center" :background-color "#FFF" :color "#272822"}}]
              (map-indexed (fn [i cell-atom]
                ^{:key i} [:td {:style {:text-align "center" :background-color "#FFF" :color "#272822"}}
                  (index-to-label i)]) @first-row-atom)]))
        (doall (map-indexed 
          (fn [i row-atom]
            ^{:key i} [row-view i row-atom]) @source))]]])

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
; delete(dst)
; sort(letter, method): sorts based on the specified column, uses the method specified (number/datetime/string)
; is tempting to use a simple binding with reagent
; should support up to 10,000 cells

(def default-cell
  {:c "#FFF"
   :bgc "#272822"
   :s ""
   :h 1})

(defn display [new-source]
  (let [clj-src (js->clj new-source)]
    (reset! source
      (into []
        (for [row clj-src]
          (r/atom
            (into []
            (for [cell row]
              (r/atom {
                :c "#FFF"
                :bgc "#272822"
                :s cell
                :h 1
              })))))))))

(defn add-column [column]
  (when-not column
    (doseq [row-atom @source]
      (swap! row-atom
        (fn [row]
          (conj row (r/atom default-cell)))))))

(defn add-row [index]
  (when-not index
    (let [width (apply max (map #(count @%) @source))]
      (swap! source
        #(conj % (r/atom (into [] (for [i (range width)] (r/atom default-cell)))))))))

(defn copy [src dst])
(defn move [src dst])
(defn delete [tgt])
(defn sort [column method])

(set! (.-excel js/window)
  (clj->js {
    :display display
    :add_column add-column
    :add_row add-row
    :copy copy
    :move move
    :delete delete
    :sort sort}))
