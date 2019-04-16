(ns flo.client.ace
  (:require [cljsjs.ace]))

(def instance (atom nil))
(defn new-instance []
  (js/ace.config.set "basePath" "ace")
  (reset! instance (js/ace.edit "editor"))
  (.setTheme @instance "ace/theme/monokai")
  (.setMode (.-session @instance) "ace/mode/javascript"))
