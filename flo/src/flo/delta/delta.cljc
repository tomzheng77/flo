(ns flo.delta.delta)

(def example-1 {:ops []})

(defn new
  ([] (new []))
  ([ops]
   (assert (seq? ops))
   {:ops ops}))

(defn insert [this arg attributes]
  (if (and (string? arg) (= 0 (count arg)))
    this
    (let [new-op {:insert arg :attributes attributes}]
      (assoc this :ops (conj (:ops this) new-op)))))

(defn delete [this length]
  (if (>= 0 length)
    this
    (assoc this :ops (conj (:ops this) {:delete length}))))

(defn retain [this length attributes]
  (if (>= 0 length)
    this
    (let [new-op {:retain length :attributes attributes}]
      (assoc this :ops (conj (:ops this) new-op)))))

(defn replace-last [this op]
  (assoc this :ops
    (-> this
        (:ops)
        (drop-last)
        (conj op)
        (vec))))

(defn not-nil? [x] (not (nil? x)))

(defn insert-at [this index new-op]
  (if (>= index (count (:ops this)))
    (assoc this :ops (conj (:ops this) new-op))
    (assoc this :ops
      (let [[before after] (split-at index (:ops this))]
        (vec (concat before [new-op] after))))))

(defn push [this new-op]
  (with-local-vars [index (count (:ops this)) last-op (last (:ops this))]
    (when (map? @last-op)
      (cond
        (and (integer? (:delete new-op)) (integer? (:delete @last-op)))
          (replace-last this {:delete (+ (:delete new-op) (:delete @last-op))})
        (and (integer? (:delete @last-op)) (not-nil? (:insert new-op)))
          (do
            (var-set index (dec @index))
            (var-set last-op (get (:ops this) (dec @index)))
            (if-not (map? last-op)
              (assoc this :ops (vec (concat [new-op] (:ops this))))))
        (= (:attributes new-op) (:attributes last-op))
          (cond
            (and (string? (:insert new-op)) (string? (:insert last-op)))
              (replace-last this
                {:insert (str (:insert last-op) (:insert new-op))
                 :attributes (:attributes new-op)})
            (and (integer? (:retain new-op)) (integer? (:retain last-op)))
              (replace-last this
                {:retain (+ (:retain last-op) (:retain new-op))
                 :attributes (:attributes new-op)})
            true (insert-at this @index new-op))
        true (insert-at this @index new-op)))))

(defn chop [this]
  (let [last-op (last (:ops this))]
    (if (and (not-nil? (:retain last-op)) (empty? (:attributes last-op)))
      (assoc this :ops (drop-last (:ops this))) this)))

(defn change-length [])
(defn length [])

(defn slice
  ([delta start] {:ops (subvec (:ops delta) start)})
  ([delta start end] {:ops (subvec (:ops delta) start end)}))

(defn compose [this other])
(defn concat [])
(defn diff [])
(defn each-line [])
(defn invert [])
(defn transform [])
(defn transform-position [])
