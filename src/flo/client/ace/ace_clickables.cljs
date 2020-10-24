(ns flo.client.ace.ace-clickables
  (:require [flo.client.ace.ace :as ace]
            [clojure.set :as set]
            [clojure.string :as str]
            [re-frame.core :as rf]))

;;;; allows the user to click on links directly within the editor
;;;; while a key is currently pressed (typically the ctrl key)
;;;; enabling them to navigate directly to hrefs and tag declarations

(defn $ [& args] (apply js/$ args))

(def clickable-css
  "
  .ace_clickables .ace_clickable_link {
    color: #589DF6;
    position: absolute;
    font-weight: bold;
    cursor: pointer;
    z-index: 10;
    pointer-events: auto;
    border-radius: 2px;
    background: rgba(0,0,0,0);
    opacity: 0;
    user-select: none;
    text-decoration: underline;
  }
  .ace_clickables .ace_clickable_link:hover {
    opacity: 1;
  }

  .ace_content .ace_clickable {
    pointer-events: auto;
  }")

(defn element-position [$el]
  (let [rect (.getBoundingClientRect (aget $el 0))]
    {:x (.-left rect) :y (.-top rect)}))

(defn on-after-render [_ renderer]
  (let [$scroller ($ (.-container renderer))
        $content ($ (.-content renderer))
        $clickables (.find $scroller ".ace_clickables")
        clickable-list (.find $content ".ace_clickable")]
    (.empty $clickables)
    (.each
      clickable-list
      (fn [_ cl]
        (let [$cl ($ cl)
              types (into #{} (map #(subs % 4) (str/split (.-className cl) #"\s+")))
              text (.text $cl)
              pos (element-position $cl)
              $added ($ (str
                          "<div class='ace_clickable_link' style='left: " (:x pos)
                          "px; top: " (:y pos) "px'>" text "</div>"))]
          (.appendTo $added $clickables)
          (.click $added
                  (fn [event]
                    (.stopPropagation event)
                    (.preventDefault event)
                    (rf/dispatch [:click-link types text]))))))))

(defn enable-clickables [this]
  (js/console.log "Clickables: Enabled")
  (.on (.-renderer this) "afterRender" on-after-render)
  (let [$clickable-layer ($ "<div class='ace_layer ace_clickables'></div>")]
    (set! (.-clickableLayer this) $clickable-layer)
    (.appendTo $clickable-layer ($ (.-container this)))
    (ace/hide-clickables this)))

(defn disable-clickables [this]
  (js/console.log "Clickables: Disabled")
  (.off (.-renderer this) "afterRender" on-after-render)
  (-> (.-container this)
      (js/$)
      (.find ".ace_layer.ace_clickables")
      (.remove)))

; add separate clickable layer
; similar to fs_previews
; it adds a layer of some input elements
(js/define
  "ace/ext/clickables"
  (clj->js ["ace/editor"])
  (fn [require]
    (.importCssString (require "../lib/dom") clickable-css "ace_clickables")
    (.defineOptions
      (require "../config")
      (.. (require "ace/editor") -Editor -prototype)
      "editor"
      (clj->js
        {:enableClickables
         {:set   (fn [val] (this-as this (if val (enable-clickables this) (disable-clickables this))))
          :value false}}))))

(js/window.require
  (clj->js ["ace/ext/clickables"])
  (fn []))
