(ns rocks.pho.stock.analyst.analysis
  (:require [clojure.tools.logging :as log]
            [incanter.core :as incanter]

            [rocks.pho.stock.analyst.config :refer [trans-type]]
            [rocks.pho.stock.analyst.utils :as utils]))

(defn analysis-one [data & {:keys [big-amount big-volume start-time end-time]
                            :or {big-amount 0
                                 big-volume 0
                                 start-time "09:00:00"
                                 end-time "15:30:00"}}]
  (let [cleaned-data-volume (incanter/add-derived-column :volume-int [:volume] #(Integer. %) data)
        cleaned-data (incanter/add-derived-column :amount-int [:amount] #(Integer. %) cleaned-data-volume)
        first-one (incanter/tail 1 cleaned-data)
        first-trans {:time (incanter/$ 0 :time first-one)
                     :price (Float/parseFloat (incanter/$ 0 :price first-one))
                     :volume (incanter/$ 0 :volume-int first-one)
                     :amount (incanter/$ 0 :amount-int first-one)}
        last-one (incanter/head 1 cleaned-data)
        last-trans {:time (incanter/$ 0 :time last-one)
                    :price (Float/parseFloat (incanter/$ 0 :price last-one))
                    :volume (incanter/$ 0 :volume-int last-one)
                    :amount (incanter/$ 0 :amount-int last-one)}
        time-filtered-data (incanter/$where {:time {:$fn (fn [t]
                                                           (and (>= (compare t start-time)
                                                                    0)
                                                                (<= (compare t end-time)
                                                                    0)))}} cleaned-data)
        big-trans-volume (incanter/$where {:volume-int {:$fn (fn [volume]
                                                               (>= volume big-volume))}}
                                          time-filtered-data)
        big-trans (incanter/$where {:amount-int {:$fn (fn [amount]
                                                        (>= amount big-amount))}}
                                   big-trans-volume)
        agg-big-trans (incanter/aggregate [:amount-int] [:type]
                                          :dataset big-trans
                                          :rollup-fun :sum)
        type-data-fn (fn [data type]
                       (incanter/query-dataset data {:type type}))
        type-amount-fn (fn [data tp]
                         (let [type-data (type-data-fn data (tp trans-type))]
                           (if-not (empty? (:rows type-data))
                             (incanter/$ 0 :amount-int type-data)
                             0)))
        big-buy-trans-amount (type-amount-fn agg-big-trans :buy)
        big-sell-trans-amount (type-amount-fn agg-big-trans :sell)
        big-normal-trans-amount (type-amount-fn agg-big-trans :normal)
        type (key (apply max-key val {"buy" big-buy-trans-amount
                                      "sell" big-sell-trans-amount
                                      "normal" big-normal-trans-amount}))]
    {:type type
     :buy-trans-amount big-buy-trans-amount
     :sell-trans-amount big-sell-trans-amount
     :normal-trans-amount big-normal-trans-amount
     :first-trans first-trans
     :last-trans last-trans}))

(defn analysis-one-day [path dt & {:keys [big-amount big-volume start-time end-time top]
                                   :or {big-amount 0
                                        big-volume 0
                                        start-time "09:00:00"
                                        end-time "15:30:00"
                                        top 10}}]
  (let [today-dir (str path "/" dt)
        list-file (str today-dir "/list")
        codes (with-open [r (clojure.java.io/reader list-file)]
                (loop [codes []
                       l (.readLine r)]
                  (if (nil? l)
                    codes
                    (let [[code weight name] (clojure.string/split l #",")]
                      (recur (conj codes {:code code
                                          :weight weight
                                          :name name})
                             (.readLine r))))))
        one-day (incanter/to-dataset
                 (loop [codes codes
                        re []]
                   (if (empty? codes)
                     re
                     (let [c (first codes)
                           code (:code c)
                           code-file (str today-dir "/" code ".csv")
                           one (assoc (analysis-one (utils/read-csv code-file)
                                                    :big-amount big-amount
                                                    :big-volume big-volume
                                                    :start-time start-time
                                                    :end-time end-time)
                                      :code code
                                      :name (:name c)
                                      :weight (:weight c))]
                       (recur (rest codes)
                              (conj re one))))))
        one-day-top-buy-trans-amount (incanter/sel (incanter/$order :buy-trans-amount
                                                                    :desc
                                                                    (incanter/$where {:type {:$fn (fn [t]
                                                                                                    (= t "buy"))}}
                                                                                     one-day))
                                                   :rows (range top))
        one-day-top-sell-trans-amount (incanter/sel (incanter/$order :sell-trans-amount
                                                                     :desc
                                                                     (incanter/$where {:type {:$fn (fn [t]
                                                                                                     (= t "sell"))}}
                                                                                      one-day))
                                                    :rows (range top))
        agg-trans (incanter/aggregate [:buy-trans-amount
                                       :sell-trans-amount
                                       :normal-trans-amount]
                                      :type
                                      :dataset one-day
                                      :rollup-fun :sum)
        agg-count (incanter/aggregate [:count]
                                      :type
                                      :dataset one-day
                                      :rollup-fun :count)
        summary (incanter/$join [:type :type] agg-trans agg-count)]
    {:summary summary
     :top-trans-amount (incanter/conj-rows one-day-top-buy-trans-amount
                                           one-day-top-sell-trans-amount)}))

(defn summary-one-day [data-path dt]
  (let [all (analysis-one-day data-path dt)
        morning (analysis-one-day data-path dt :end-time "12:00:00")
        afternoon (analysis-one-day data-path dt :start-time "12:30:00")
        volume-400 (analysis-one-day data-path dt :big-volume 400)
        amount-10 (analysis-one-day data-path dt :big-amount 100000)]
    {:all all
     :morning morning
     :afternoon afternoon
     :volume-400 volume-400
     :amount-10 amount-10}))

(defn analysis-one-some-days [data-path start-dt end-dt & {:keys [big-amount big-volume]
                                                           :or {big-amount 100000
                                                                big-volume 0}}]
  (let [analysis-re (loop [dt start-dt
                           re []]
                      (if (> (compare dt end-dt)
                             0)
                        re
                        (let [data (str data-path "/" dt ".csv")]
                          (if (.exists (clojure.java.io/as-file data))
                            (recur (utils/calendar-add-days dt 1)
                                   (conj re (assoc (analysis-one (utils/read-csv data) :big-amount big-amount :big-volume big-volume)
                                                   :dt dt)))
                            (recur (utils/calendar-add-days dt 1)
                                   re)))))
        re-dealed (map (fn [one]
                         (let [total-amount (bigdec (+ (:buy-trans-amount one)
                                                       (:sell-trans-amount one)
                                                       (:normal-trans-amount one)))]
                           (assoc one
                                  :buy-trans-rate (with-precision 2 (/ (:buy-trans-amount one) total-amount))
                                  :sell-trans-rate (with-precision 2 (/ (:sell-trans-amount one) total-amount))
                                  :normal-trans-rate (with-precision 2 (/ (:normal-trans-amount one) total-amount))
                                  :first-price (:price (:first-trans one))
                                  :last-price (:price (:last-trans one))))) analysis-re)]
    re-dealed))

(defn prn-analysis-one-some-days [data-path start-dt end-dt big-amount big-volume]
  (let [re (analysis-one-some-days data-path start-dt end-dt :big-amount big-amount :big-volume big-volume)]
    (doseq [one re]
      (prn (str (:dt one) "---"
                (utils/date2week (:dt one)) "---"
                (:type one) "---"
                (with-precision 3 (/ (:buy-trans-amount one)
                                     100000000M)) "亿---"
                (:buy-trans-rate one) "---"
                (with-precision 3 (/ (:sell-trans-amount one)
                                     100000000M)) "亿---"
                (:sell-trans-rate one) "---"
                (:normal-trans-rate one) "---"
                (:first-price one) "---"
                (:last-price one))))))
