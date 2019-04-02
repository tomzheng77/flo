(ns flo.quill
  (:require
    [cljsjs.quill]
    [flo.functions :refer [json->clj]]
    [clojure.string :as str]))

(defn compose-delta [old-delta new-delta]
  (.compose old-delta new-delta))

; read-only atom containing the contents displayed in quill
(def contents (atom {}))

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

; create the quill editor instance
(def quill
  (new js/Quill "#editor"
    (clj->js {"modules" {"toolbar" toolbar-options "imageResize" {}}
              "theme"   "snow"})))

(def quill-editor
  (aget (.. (.getElementById js/document "editor") -children) 0))

(.on quill "text-change"
     (fn [new-delta old-delta source]
       (reset! contents (json->clj (compose-delta old-delta new-delta)))))

(def last-contents (atom nil))

(defn enable-edit [] (.enable quill))
(defn disable-edit [] (.disable quill))

(defn get-text [] (.getText quill))

(defn get-contents [] (json->clj (.getContents quill)))
(defn set-contents [contents]
  (.setContents quill (clj->js contents)))

(defn set-selection
  ([] (.setSelection quill nil))
  ([index length]
   (.setSelection quill index length)))

(defn get-bounds [index length]
  (js->clj (.getBounds quill index length)))

(defn scroll-by [x y]
  (.scrollBy quill-editor x y))

(defn goto-substr
  [substr]
  (let [index (str/index-of (get-text) substr) length (count substr)]
    (when index
      (set-selection index length)
      (let [bounds (get-bounds index length)]
        (scroll-by (get bounds "left")
                   (get bounds "top")))
      index)))
