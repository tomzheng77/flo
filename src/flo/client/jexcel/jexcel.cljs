(ns flo.client.jexcel.jexcel
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [re-frame.core :as rf]))

; excel format instructions
; type: "sheet"
; version: 1
; widths: widths of each column, each can be string or integer
; data: 2D array

(defn new-instance [element-id]
  (js/jexcel
    (js/document.getElementById element-id)
    (clj->js
      {:data (vec (for [row (range 100)] (vec (for [col (range 20)] col))))
       :wordWrap true
       :search true
       :rowResize true
       :rowDrag true
       :columnResize true
       :columnDrag true
       :defaultColWidth 100
       :tableOverflow false})))

(defn save [this]
  {:type "sheet"
   :version 1
   :widths (js->clj (.getWidth this))
   :data (js->clj (.getData this))})

(defn load [this saved]
  (.setData this (clj->js (:data saved)))
  (doseq [[i w] (map-indexed vector (:widths))]
    (.setWidth this (clj->js w))))
