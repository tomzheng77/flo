(ns flo.client.model.query
  (:require [clojure.string :as str]
            [flo.client.model.selection :as s]
            [flo.client.constants :as c]))

;; parses the text which the user entered into the navigation box
;; into separate components, such as name and search keyword
;; by default, the query is the name (or abbreviation) of a note
;; if it contains a '@', then the part after the '@' will be treated as a search
;; if it contains a ':', then the part after the ':' will be treated as a selection range
(defn parse [s]
  (if-not s
    {:keyword nil :search nil :selection nil}
    (cond
      (re-find #"@" s)
      (let [[name search] (str/split s #"@" 2)]
        {:keyword (if (not-empty name) name)
         :search
         (str "[" (str/upper-case search) "]")})

      (re-find #":" s)
      (let [[name range-str] (str/split s #":" 2)]
        {:keyword (if (not-empty name) name)
         :selection (-> range-str s/str-to-range s/range-to-selection)})

      :else {:keyword (if (not-empty s) s)})))

(defn to-string [query]
  (let [{:keys [keyword search selection]} query
        keyword-str (or keyword "")
        search-str (if search (str "@" (c/remove-brackets search)))
        selection-str (if selection (str ":" (s/range-to-str (-> selection :ranges (first)))) "")]
    (str keyword-str search-str selection-str)))
