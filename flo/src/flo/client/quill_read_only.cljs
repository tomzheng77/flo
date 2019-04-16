(ns flo.client.quill-read-only
  (:require
    [flo.client.functions :refer [json->clj find-all]]
    [clojure.data :refer [diff]]
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

(defn enable-edit [] (.enable @instance))
(defn disable-edit [] (.disable @instance))
(defn focus [] (.focus @instance))

(defn get-text [] (.getText @instance))

(defn get-content [] (json->clj (.getContents @instance)))
(defn set-content [contents]
  (.setText @instance "")
  (.setContents @instance (clj->js contents)))

(defn set-selection
  ([] (.setSelection @instance nil))
  ([index length]
   (.setSelection @instance index length)))

(defn set-cursor-at-selection []
  (let [selection (.getSelection @instance)]
    (when selection
      (.setSelection @instance (.-index selection)))))

(defn get-bounds [index length]
  (js->clj (.getBounds @instance index length)))

(defn scroll-by [x y]
  (.scrollBy @instance-editor x y))

(defn goto [index length]
  (set-selection index length)
  (let [bounds (get-bounds index length)
        height (.-clientHeight @instance-editor)]
    (scroll-by (get bounds "left")
               (- (get bounds "top") (/ height 4)))))

(defn find-and-goto
  [substr]
  (let [index (str/index-of (get-text) substr) length (count substr)]
    (when index (goto index length) index)))

(defn format-changed [old-format new-format]
  (let [[old new same] (diff old-format new-format)]
    (some (fn [[k v]] (or (some? v) (and old (old k)))) new)))

(defn format-text [start length format]
  (let [old-format (js->clj (.getFormat @instance start length))]
    (when (format-changed old-format format)
      (.formatText @instance start length (clj->js format)))))

(defn highlight-tags []
  (let [text (get-text)]
    (doseq [match (find-all text #"\[=?[A-Z0-9]+=?\]")]
      (format-text (:start match) (:length match) {"bold" true "color" "#3da1d2"})
      (format-text (:end match) 1 {"bold" nil "color" nil}))))

(defn new-instance []
  (.remove (js/$ "#editor-read-only .ql-toolbar"))
  (reset! instance (new js/Quill "#editor-read-only" (clj->js {"modules" {"toolbar" toolbar-options "imageResize" {} "syntax" true} "theme" "snow"})))
  (.on @instance "text-change"
       (fn [new-delta old-delta source]
         (reset! content (json->clj (compose-delta old-delta new-delta)))))
  (reset! instance-editor
          (aget (.. (.getElementById js/document "editor-read-only") -children) 0)))
