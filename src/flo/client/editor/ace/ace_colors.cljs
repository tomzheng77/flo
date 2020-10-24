(ns flo.client.editor.ace.ace-colors
  (:require [clojure.string :as str]))

;;;; sets the text color of hex literals to the color they specify
;;;; e.g. #FF0000 would appear red and #006699 would appear blue

(defn $ [& args] (apply js/$ args))

(defn on-after-render [_ renderer]
  (let [$content ($ (.-content renderer))
        color-literals (.find $content ".ace_color.ace_literal")]
    (.each color-literals
      (fn [_ cl]
        (let [$cl ($ cl)
              types (into [] (map #(subs % 4) (str/split (.-className cl) #"\s+")))
              color (nth types 2)]
          (.css $cl "color" (str "#" color)))))))

(defn enable [this] (.on (.-renderer this) "afterRender" on-after-render))
(defn disable [this] (.off (.-renderer this) "afterRender" on-after-render))

(js/define
  "ace/ext/colors" (clj->js ["ace/editor"])
  (fn [require]
    (.defineOptions
      (require "../config") (.. (require "ace/editor") -Editor -prototype) "editor"
      (clj->js
        {:enableColors
         {:set   (fn [val] (this-as this (if val (enable this) (disable this))))
          :value false}}))))

(js/window.require
  (clj->js ["ace/ext/colors"])
  (fn []))
