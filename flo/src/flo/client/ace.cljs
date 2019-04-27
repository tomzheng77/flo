(ns flo.client.ace
  (:require [cljsjs.ace]
            [clojure.set :as set]))

(def instance (atom nil))
(defn new-instance [element-id]
  (js/ace.config.set "basePath" "ace")
  (let [instance (js/ace.edit element-id)]
    (.setTheme instance "ace/theme/monokai")
    (.setMode (.-session instance) "ace/mode/markdown")
    instance))

(defn set-text [this text]
  (.setValue (.-session this) text))

(defn get-text [this]
  (.getValue this))

(defn set-read-only [this value]
  (.setReadOnly this value))

(defn navigate
  "navigates to the next occurrence of the <search> tag"
  ([this search] (navigate this search {}))
  ([this search opts]
   (if (and search (not-empty search))
     (let [settings (clj->js (set/union {"caseSensitive" true "regExp" true "backwards" false} opts))]
       (.find this (str "\\[=?" search "=?\\]") settings)))))
