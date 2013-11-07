(ns active.import
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.java.io :as io]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce])
  (:use [clojure.data.zip.xml]
        [active.geo]))

(defrecord Fileinfo [user type file])
(defrecord Trackpoint [user activity latitude longitude altitude date])

(def types {:run "Run" :ride "Ride"})
(def throttle-stream (= "true" (System/getProperty "throttle.stream")))

(defn directories-from [base]
  (rest (for [file (file-seq (clojure.java.io/file base)) :when (.isDirectory file)] file)))

(defn files-from [base]
  (rest (for [file (file-seq (clojure.java.io/file base)) :when (.isFile file)] file)))

(defn name-from [file]
  (.getName file))

(defn of-type [type files]
  (filter #(.contains (name-from %) (str type ".gpx")) files))

(defn strip-type-from [name type]
  (clojure.string/replace name (str "-" (type types) ".gpx") ""))

(defn fileinfo-from [user type file]
  (Fileinfo. (str user "-" (strip-type-from (name-from file) type)) type file))

(defn scan-directories [directory]
  (let [user (name-from directory)
        files (files-from directory)
        runs (of-type (:run types) files)
        rides (of-type (:ride types) files)]
    (concat (map (partial fileinfo-from user :run) runs) (map (partial fileinfo-from user :ride) rides))))

(defn all-fileinfo [base]
  (flatten (map scan-directories (directories-from base))))

(defn as-today [date]
  (let [now (time/today)
        then (time/to-time-zone (coerce/from-string date) (time/default-time-zone))]
    (time/date-time (time/year now) (time/month now) (time/day now) (time/hour then) (time/minute then) (time/second then) (time/milli then))))

(defn trackpoints-from [fileinfo]
  (let [contents (zip/xml-zip (xml/parse (:file fileinfo)))
        trackpoints (xml-> contents :trk :trkseg :trkpt)]
    (map #(Trackpoint. (:user fileinfo)
                  (:type fileinfo)
                  (attr % :lat)
                  (attr % :lon)
                  (first (xml-> % :ele text))
                  (as-today (first (xml-> % :time text)))) trackpoints)))


(def earliest-event (Trackpoint. "dummy" :run 0 0 0 (time/from-now (time/days 1))))

(defn earlier-of [a b]
  (if (time/before? (:date a) (:date b))
    a
    b))

(defn next-if-match [trackpoint trackpoints]
  (if (= trackpoint (first trackpoints))
    (next trackpoints)
    trackpoints))

(def feed (atom (remove empty? (map trackpoints-from (all-fileinfo "resources")))))

(defn earliest-from [activities]
  (loop [current-earliest earliest-event
         events activities]
    (if (nil? events)
      current-earliest
      (recur (earlier-of current-earliest (first (first events))) (next events)))))

(defn next-from [events]
  (if (= 0 (count events))
      nil
      (let [earliest (earliest-from events)]
        (reset! feed (remove nil? (map (partial next-if-match earliest) events)))
        earliest)))


(def first-one (atom earliest-event))
(def start-time (atom (time/now)))

(defn throttle [next-event]
  (let [time-to-wait (- (time/in-seconds (time/interval (:date @first-one) (:date next-event))) (time/in-seconds (time/interval @start-time (time/now))))]
    (if (> time-to-wait 0) (Thread/sleep (* 1000 time-to-wait)))
    (println next-event)
    next-event))

(defn next-from-feed []
  (let [next-event (next-from @feed)]
    (if (= earliest-event @first-one)
      (do
        (reset! first-one next-event)
        (reset! start-time (time/now))))
    (if throttle-stream
      (throttle next-event)
      next-event)))

(defn as-tuples [trackpoint]
    [(:user trackpoint) (:activity trackpoint) (:latitude trackpoint) (:longitude trackpoint) (:altitude trackpoint) (:date trackpoint)])

(defn current-speed [trackpoint-from trackpoint-to]
  (let [distance-travelled (distance-between trackpoint-from trackpoint-to)
        number-of-hours (/ (time/in-millis (time/interval (:date trackpoint-from) (:date trackpoint-to))) 3600000)]
    (/ distance-travelled number-of-hours)))
