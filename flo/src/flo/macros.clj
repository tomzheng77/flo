(ns flo.macros)

(defmacro console-log
  [& args]
  `(.log js/console ~@args))
