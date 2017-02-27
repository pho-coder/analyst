(ns rocks.pho.stock.analyst.utils
  (:require [clojure.tools.logging :as log]))

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
