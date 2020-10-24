(ns flo.client.editor.editor-excel
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer [go go-loop]]
    [flo.client.macros :refer [console-log]])
  (:require
    [flo.client.functions :refer [json->clj current-time-millis splice-last find-all to-clj-event]]
    [flo.client.constants :as c]
    [cljs.core.match :refer-macros [match]]
    [cljs.reader :refer [read-string]]
    [cljs.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.data.avl :as avl]
    [cljsjs.moment]
    [goog.crypt.base64 :as b64]
    [reagent.core :as r]
    [reagent.dom :as rd]
    [re-frame.core :as rf]
    [clojure.set :as set]
    [testdouble.cljs.csv :as csv]))

(def line-height 20)
(def number-column-width 50)

(defn on-input [i j cell-atom textarea]
  (let [div (.-parentNode textarea)
        td (.-parentNode div)
        _  (set! (.-height (.-style div)) (str line-height "px"))
        value (.-value textarea)
        height (.-scrollHeight textarea)
        bgc (atom (last (first (re-seq #"<B:(#[A-F0-9]{3}|#[A-F0-9]{6})>" value))))
        c (atom (last (first (re-seq #"<C:(#[A-F0-9]{3}|#[A-F0-9]{6})>" value))))
        r? (not (nil? (re-seq #"<R>" value)))
        g? (not (nil? (re-seq #"<G>" value)))
        b? (not (nil? (re-seq #"<B>" value)))]
    (when (and (not @bgc) (not @c))
      (cond
        r? (do (reset! bgc "#F00") (reset! c "#FFF"))
        g? (do (reset! bgc "#0B0") (reset! c "#FFF"))
        b? (do (reset! bgc "#069") (reset! c "#FFF"))))
    (when (not @bgc) (reset! bgc "#272822"))
    (when (not @c) (reset! c "#FFF"))
    (reset! cell-atom value)
    (set! (.-backgroundColor (.-style td)) @bgc)
    (set! (.-height (.-style div)) (str (* (js/Math.round (/ height line-height)) line-height) "px"))
    (set! (.-color (.-style textarea)) @c)))


(defn on-click-cell [event]
  (let [td (.-currentTarget event)
        div (aget (.-childNodes td) 0)
        textarea (aget (.-childNodes div) 0)]
    (.focus textarea)))


(defn width-from-cell-atom-v [cell-atom-v]
  (let [value cell-atom-v
        width (last (first (re-seq #"<W:([0-9]+)>" value)))]
    (max 15 (if width (read-string width) 100))))


(defn col [cell-atom]
  [:col {:style {:width (width-from-cell-atom-v @cell-atom) :min-width (width-from-cell-atom-v @cell-atom)}}])


(defn colgroup [row-atom]
  [:colgroup [:col {:style {:width number-column-width}}] (map-indexed (fn [i cell-atom] ^{:key i} [col cell-atom]) @row-atom)])


(defn cell-view [i j cell-atom]
  (fn []
    (r/create-class {
      :reagent-render (fn [i j cell-atom]
      [:td {:on-click #(on-click-cell %) :style {:cursor "text"}}
       [:div
        [:textarea.cell {:style {
         :width "100%"}
         :value @cell-atom
         :on-change #(on-input i j cell-atom (.-currentTarget %))}]]])

      :component-did-mount
      (fn [comp]
        (let [td (rd/dom-node comp)
              div (aget (.-childNodes td) 0)
              textarea (aget (.-childNodes div) 0)]
          (on-input i j cell-atom textarea)))

      :component-did-update
      (fn [comp]
        (let [td (rd/dom-node comp)
              div (aget (.-childNodes td) 0)
              textarea (aget (.-childNodes div) 0)]
          (on-input i j cell-atom textarea)))})))


(defn row-view [i row-atom]
  [:tr [:td {:style {:text-align "center" :background-color "#FFF" :color "#272822"}} (+ i 1)]
    (doall (map-indexed (fn [j cell-atom] ^{:key [i j]}
      [cell-view i j cell-atom]) @row-atom))])


(defn width-sum [source]
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


(defn view-render [source active?]
  (fn []
    [:div.container-excel {:style {:height "100%" :display (if @active? "block" "none") :overflow :scroll}}
      [:table {:style {:table-layout :fixed :width (width-sum source)}}
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
              ^{:key i} [row-view i row-atom]) @source))]]]))

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

(defn display [source array-2d]
  (let [clj-src (js->clj array-2d)
        w (apply max (map count array-2d))]
    (reset! source
      (into []
        (for [row clj-src]
          (r/atom
            (into []
              (for [cell-index (range w)]
                (r/atom (str (get row cell-index)))))))))))

(defn add-column [this column]
  (when-not column
    (doseq [row-atom @(:source this)]
      (swap! row-atom
        (fn [row]
          (conj row (r/atom "")))))))

(defn add-row [this index]
  (when-not index
    (let [width (apply max (map #(count @%) @(:source this)))]
      (swap! (:source this)
        #(conj % (r/atom (into [] (for [i (range width)] (r/atom "")))))))))

(defn copy [src dst])
(defn move [src dst])
(defn delete [tgt])
(defn sort-rows [column method])

(set! (.-excel js/window)
  (clj->js {
    :display display
    :add_column add-column
    :add_row add-row
    :copy copy
    :move move
    :delete delete
    :sort_rows sort-rows}))

; an atom
; of vector of atoms (rows)
; of vector of atoms (cols)
; of strings (cells)
(def example-source (r/atom
  (into []
    (for [row (range 150)]
      (r/atom
        (into []
        (for [col (range 10)]
          (r/atom (str row "-" col)))))))))

; ========== [PUBLIC METHODS] ==========

(defn new-instance
  ([] (new-instance {}))
  ([{:keys [read-only? event-handler init-active?]}]
   (let [source (r/atom []) active? (r/atom (if (nil? init-active?) true init-active?))]
     {:instance-type :client-excel-editor
      :source source
      :view (fn []
        (r/create-class {
          :reagent-render #(view-render source active?)}))
      :active? active?
      :event-handler event-handler})))

(defn get-content [this]
  (let [source (:source this)
        array-2d
        (into []
          (for [row-atom @source]
            (into []
              (for [cell-atom @row-atom] @cell-atom))))]
    (csv/write-csv array-2d :quote? true)))

(defn set-content [this content]
  (let [source (:source this)
        content-fixed (str/replace (str/replace content #"\r\n|\n\r" "\n") #"\r" "\n")
        array-2d (csv/read-csv content-fixed)]
    (display source array-2d)))

(defn open-note
  ([this note] (open-note this note nil))
  ([this {:keys [content]} open-opts] (set-content this content)))

(defn goto-search [this search backwards])
(defn insert-image [this image-id])
(defn focus [this])

(defn on-press-key [this event])
(defn on-release-key [this event])
(defn on-window-blur [this event])
