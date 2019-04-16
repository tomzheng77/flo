(ns flo.client.ace)

(def instance (atom nil))
(defn new-instance []
  (reset! instance (js/ace.edit "editor"))
  (.setTheme @instance "ace/theme/monokai")
  (.setMode (.-session @editor) "ace/mode/javascript"))
