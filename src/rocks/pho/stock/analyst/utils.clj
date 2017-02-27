(ns rocks.pho.stock.analyst.utils)

(defn get-today-date
  []
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") (java.util.Date.)))

(defn get-hour
  []
  (.format (java.text.SimpleDateFormat. "HH") (java.util.Date.)))
