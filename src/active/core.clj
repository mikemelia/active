(ns active.core
  (:use ;[active.earth]
        [active.import]
        [active.topology])
  (:gen-class))

(defn -main [& args]
 ;; (create-model -38.3825180 144.7930020 9.6)
  (run-local!))
