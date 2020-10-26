(ns flo.client.constants)

(def regex-declaration #"\[[A-Z0-9]+\]")
(def regex-definition #"\[[A-Z0-9]+=\]")
(def regex-reference #"\[[A-Z0-9]+@[A-Z0-9]*\]")
(def regex-navigable "[A-Z0-9]+(@[A-Z0-9]*|=)?")
(def regex-alphanumeric #"^[A-Za-z0-9]$")
(def regex-url-hash-part #"(?<=#)[^#]*$")

; converts a declaration e.g. [ABC] to a definition e.g. [ABC=] or vice versa
(defn declaration-to-definition [text] (str text "="))
(defn definition-to-declaration [text] (subs text 0 (dec (count text))))

; removes the first and last characters of a string
(defn remove-brackets [text] (if (< (count text) 2) text (subs text 1 (dec (count text)))))
