(ns rocks.pho.stock.analyst.utils
  (:import (java.io FileReader)
           (com.opencsv CSVReader))
  (:require [clojure.tools.logging :as log]
            [incanter.core :as incanter]
            [clojure.data.csv :as csv]))

(defn read-csv [path]
  (let [parse-line-fn (fn [line]
                        (vec line))
        data (with-open [reader ^CSVReader (CSVReader. (clojure.java.io/reader path))]
               (loop [lines []]
                     (if-let [line (.readNext reader)]
                       (recur (conj lines (parse-line-fn line)))
                       lines)))
        header (map keyword (first data))]
    (incanter/dataset header (rest data))))

(defn write-csv [data path]
  (with-open [f-out (clojure.java.io/writer path)]
    (csv/write-csv f-out [(map name (incanter/col-names data))])
    (csv/write-csv f-out (incanter/to-list data))))

(defn get-today-date
  []
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") (java.util.Date.)))

(defn get-hour
  []
  (.format (java.text.SimpleDateFormat. "HH") (java.util.Date.)))

(defn check-finish [data-path dt]
  (try
    (let [finish-file (clojure.java.io/as-file (str data-path "/" dt "/finish"))]
      (.exists finish-file))
    (catch Exception e
      (log/warn e)
      false)))

(defn write-summary-one-day
  [summary-data-path dt summary]
  (let [today-path (str summary-data-path "/" dt)]
    (when-not (.exists (clojure.java.io/as-file today-path))
      (clojure.java.io/make-parents (str today-path "/test")))
    (write-csv (:summary (:all summary))
               (str today-path "/all"))
    (log/info "write all csv")
    (write-csv (:top-trans-amount (:all summary))
               (str today-path "/all.top-trans-amount"))
    (log/info "write all.top-trans-amount csv")
    (write-csv (:summary (:morning summary))
               (str today-path "/morning"))
    (log/info "write morning csv")
    (write-csv (:summary (:afternoon summary))
               (str today-path "/afternoon"))
    (log/info "write afternoon csv")
    (write-csv (:summary (:volume-400 summary))
               (str today-path "/volume-400"))
    (log/info "write volume-400 csv")
    (write-csv (:summary (:amount-10 summary))
               (str today-path "/amount-10"))
    (log/info "write amount-10 csv")))

(defn str2date
  ([date-str fm]
   (.parse (java.text.SimpleDateFormat. fm) date-str))
  ([date-str]
   (str2date date-str "yyyy-MM-dd")))

(defn date2str
  ([date fm]
   (.format (java.text.SimpleDateFormat. fm) date))
  ([date]
   (date2str date "yyyy-MM-dd")))

(defn calendar-add-days
  ([date-str days fm]
   (let [cal (java.util.Calendar/getInstance)
         date (str2date date-str fm)]
     (.setTime cal date)
     (.add cal (java.util.Calendar/DAY_OF_MONTH) days)
     (date2str (.getTime cal) fm)))
  ([date-str days]
   (calendar-add-days date-str days "yyyy-MM-dd")))
