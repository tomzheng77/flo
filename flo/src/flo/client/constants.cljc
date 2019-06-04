(ns flo.client.constants)

(def declaration-regex #"\[[A-Z0-9]+\]")
(def definition-regex #"\[[A-Z0-9]+=\]")
(def reference-regex #"\[[A-Z0-9]+@[A-Z0-9]*\]")
(def any-navigation-inner "[A-Z0-9]+(@[A-Z0-9]*|=)?")
(def alphanumerical-regex #"^[A-Za-z0-9]$")
(def url-hash-regex #"(?<=#)[^#]*$")

(defn declaration-to-definition [text] (str text "="))
(defn definition-to-declaration [text] (subs text 0 (dec (count text))))
(defn remove-brackets [text] (subs text 1 (dec (count text))))
