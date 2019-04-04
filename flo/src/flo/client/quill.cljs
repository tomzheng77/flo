(ns flo.client.quill
  (:require
    [flo.client.functions :refer [json->clj]]
    [clojure.string :as str]
    [cljsjs.jquery]
    [cljsjs.quill]
    [quill-image-resize-module :as resize]))

(defn compose-delta [old-delta new-delta]
  (.compose old-delta new-delta))

; read-only atom containing the contents displayed in quill
(def content (atom {}))

(def instance (atom nil))
(def instance-editor (atom nil))

(def toolbar-options
  [["bold" "italic" "underline" "strike"]
   ["blockquote" "code-block"]
   [{"header" 1} {"header" 2}]
   [{"list" "ordered"} {"list" "bullet"}]
   [{"script" "sub"} {"script" "super"}]
   [{"indent" "-1"} {"indent" "+1"}]
   [{"direction" "rtl"}]
   [{"size" ["small" false "large" "huge"]}]
   [{"header" [1 2 3 4 5 6 false]}]
   ["link" "image" "video" "formula"]
   [{"color" []} {"background" []}]
   [{"font" []}]
   [{"align" []}]
   ["clean"]])

(defn new-instance []
  (.remove (js/$ ".ql-toolbar"))
  (reset! instance (new js/Quill "#editor" (clj->js {"modules" {"toolbar" toolbar-options "imageResize" {}} "theme" "snow"})))
  (.on @instance "text-change"
    (fn [new-delta old-delta source]
      (reset! content (json->clj (compose-delta old-delta new-delta)))))
  (reset! instance-editor
    (aget (.. (.getElementById js/document "editor") -children) 0)))

(defn enable-edit [] (.enable @instance))
(defn disable-edit [] (.disable @instance))
(defn focus [] (.focus @instance))

(defn get-text [] (.getText @instance))

(defn get-content [] (json->clj (.getContents @instance)))
(defn set-content [contents]
  (.setContents @instance (clj->js contents)))

(defn set-selection
  ([] (.setSelection @instance nil))
  ([index length]
   (.setSelection @instance index length)))

(defn get-bounds [index length]
  (js->clj (.getBounds @instance index length)))

(defn scroll-by [x y]
  (.scrollBy @instance-editor x y))

(defn goto [index length]
  (set-selection index length)
  (let [bounds (get-bounds index length)]
    (scroll-by (get bounds "left")
               (get bounds "top"))))

(defn find-and-goto
  [substr]
  (let [index (str/index-of (get-text) substr) length (count substr)]
    (when index (goto index length) index)))
