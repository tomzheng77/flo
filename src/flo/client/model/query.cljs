(ns flo.client.model.query
  (:require [clojure.string :as str]
            [flo.client.model.selection :as s]))

;; parses the text which the user entered into the navigation box
;; into separate components, such as name and search keyword
;; by default, the query is the name (or abbreviation) of a note
;; if it contains a '@', then the part after the '@' will be treated as a search
;; if it contains a ':', then the part after the ':' will be treated as a selection range
(defn parse [s]
  (if-not s
    {:name nil :search nil :selection nil}
    (cond
      (re-find #"@" s)
      (let [[name search] (str/split s #"@" 2)]
        {:name name
         :search
         (if (str/starts-with? search "!")
           (str/upper-case (subs search 1))
           (str (str/upper-case search) "="))})

      (re-find #":" s)
      (let [[name range-str] (str/split s #":" 2)]
        {:name  name
         :selection (-> range-str s/str-to-range s/range-to-selection)})

      :else {:name s})))
