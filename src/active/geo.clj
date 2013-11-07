(ns active.geo)

(def earth-radius 6369)

(defn distance-in-km [a-lat a-long b-lat b-long]
  (let [lat-distance (Math/toRadians (- a-lat b-lat))
        long-distance (Math/toRadians (- a-long b-long))
        sin-distance-lat (Math/sin (/ lat-distance 2))
        sin-distance-long (Math/sin (/ long-distance 2))
        a (+ (Math/pow sin-distance-lat 2) (apply * [(Math/pow sin-distance-long 2) (Math/cos (Math/toRadians a-lat)) (Math/cos (Math/toRadians b-lat))]))
        c (* 2 (Math/atan2 (Math/sqrt a) (Math/sqrt (- 1 a))))]
    (* earth-radius c)))

(defn distance [a-latitude a-longitude b-latitude b-longitude]
  (distance-in-km (Double. a-latitude) (Double. a-longitude) (Double. b-latitude) (Double. b-longitude)))

(defn distance-between [trackpoint-from trackpoint-to]
  (distance (:latitude trackpoint-from) (:longitude trackpoint-from) (:latitude trackpoint-to) (:longitude trackpoint-to)))
