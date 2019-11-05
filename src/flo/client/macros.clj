(ns flo.client.macros)

(defmacro console-log
  [& args]
  `(.log js/console ~@args))
