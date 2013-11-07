(ns active.topology
  (:import [backtype.storm StormSubmitter LocalCluster]
           [com.google.common.cache CacheBuilder]
           [java.util.concurrent TimeUnit] )
  (:use [backtype.storm clojure config]
        [active.import]
        [active.geo]
        [active.earth]))

(defn create-cache [expiry]
  (.build (.expireAfterWrite (CacheBuilder/newBuilder) expiry (TimeUnit/MINUTES))))

(defspout trackpoint-spout ["trackpoint"]
  [conf context collector]
  (spout
   (nextTuple []
              (let [next-trackpoint (next-from-feed)]
                (if (not (nil? next-trackpoint))
                  (emit-spout! collector [next-trackpoint]))))))

(defbolt rendering-bolt [] {:prepare true}
  [conf context collector]
  (let [model-and-layer (atom (create-model))
        [model layer] @model-and-layer
       ]
    (bolt
     (execute [tuple]
              (let [trackpoint (.getValue tuple 0)
                    speed (.getDouble tuple 1)]
                (do
                    (clear model layer)
                    (add-placemark model layer (:latitude trackpoint) (:longitude trackpoint) (:altitude trackpoint) (str "speed : " (Math/round speed)))
                    (redisplay model layer)))))))

(defbolt current-speed-bolt ["trackpoint" "speed"] {:prepare true :params [window]}
  [conf context collector]
  (let [cache (create-cache window)
        id (atom 0)]
    (bolt
     (execute [tuple]
              (let [trackpoint (.getValue tuple 0)
                    name (:user trackpoint)
                    date (:date trackpoint)
                    previous (.getIfPresent cache name)
                    ]
                (if (not (or (nil? previous) (= (:date previous) (:date trackpoint))))
                  (emit-bolt! collector [trackpoint (current-speed previous trackpoint)]))
                (.put cache name trackpoint))))))

(defn close-together [close-distance from to]
  (> close-distance (distance-between from to)))

(defn all-close [close-distance trackpoint others]
  (filter #(and (not (= (:user trackpoint) (:user %))) (close-together close-distance trackpoint %)) others))

(defbolt closeness-bolt ["first" "second"] {:prepare true :params [window close-distance]}
  [conf context collector]
  (let [cache (create-cache window)
        id (atom 0)]
    (bolt
     (execute [tuple]
              (let [trackpoint (.getValue tuple 0)]
                (doseq [other (all-close close-distance trackpoint (.values (.asMap cache)))] (emit-bolt! collector [(:user trackpoint) (:user other)]))
              (.put cache name trackpoint))))))

(defbolt currently-training ["number"] {:prepare true :params [window]}
  [conf context collector]
  (let [cache (create-cache window)
        id (atom 0)]
    (bolt
     (execute [tuple]
              (let [trackpoint (.getValue tuple 0)
                    name (:user trackpoint)
                    date (:date trackpoint)
                    asMap (.asMap cache)
                    previous (.getIfPresent cache name)
                    ]
                (.put cache name trackpoint)
                ;;
                (emit-bolt! collector [(count asMap)]))))))

(defbolt speed-filter ["trackpoint" "speed"]
  [tuple collector]
  (let [speed (.getDouble tuple 1)
        trackpoint (.getValue tuple 0)]
    (if (or (> speed 40) (and (> speed 15) (= :run (:activity trackpoint))))
      (emit-bolt! collector [trackpoint speed]))))

(defbolt print-numbers []
  [tuple collector]
  (println (str "Number currently training : " (.getInteger tuple 0))))

(defbolt print-speed []
  [tuple collector]
  (println (str (:user (.getValue tuple 0)) " is currently moving at " (.getDouble tuple 1) " km/h")))

(defbolt print-close-people []
  [tuple collector]
  (println (str (.getString tuple 0) " is currently near " (.getString tuple 1))))

(defn create-topology []
  (topology
   {"1" (spout-spec trackpoint-spout)}
   {"2" (bolt-spec {"1" :shuffle}
                   (currently-training 5)
                   :p 1)
    "3" (bolt-spec {"2" :shuffle}
                   print-numbers
                   :p 5)
    "4" (bolt-spec {"1" :shuffle}
                   (current-speed-bolt 5)
                   :p 1)
    "5" (bolt-spec {"4" :shuffle}
                   print-speed
                   :p 5)
    "6" (bolt-spec {"1" :shuffle}
                   (closeness-bolt 5 0.05)
                   :p 1)
    "7" (bolt-spec {"6" :shuffle}
                   print-close-people
                   :p 5)
    "8" (bolt-spec {"4" :shuffle}
                   speed-filter)
    "9" (bolt-spec {"8" :shuffle}
                   rendering-bolt)}))


(defn run-local! []
  (let [cluster (LocalCluster.)]
    (.submitTopology cluster "trackpoints" {TOPOLOGY-DEBUG false} (create-topology))
    (Thread/sleep (apply * [24 60 60 1000]))
    (.shutdown cluster)
    ))
