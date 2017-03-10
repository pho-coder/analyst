(ns rocks.pho.stock.analyst
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]

            [rocks.pho.stock.analyst.config :refer [env]]
            [rocks.pho.stock.analyst.utils :as utils]
            [rocks.pho.stock.analyst.analysis :as analysis])
  (:gen-class))

(def cli-options
  [["-d" "--dt date" "date"
    :default (utils/get-today-date)]
   ["-c" "--code code" "code"]
   ["-s" "--start-dt dt" "start dt"]
   ["-e" "--end-dt dt" "end dt"]
   ["-a" "--big-amount amount" "big amount"
    :default 0
    :parse-fn #(Integer/parseInt %)]
   ["-v" "--big-volume volume" "big volume"
    :default 0
    :parse-fn #(Integer/parseInt %)]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["This is my program. There are many like it, but this one is mine."
        ""
        "Usage: program-name [options] action"
        ""
        "Options:"
        options-summary
        ""
        "Actions:"
        "  hs300s   Start a hs300s one day summary"
        "  one      Start a stock summary"
        ""
        "Please refer to the manual page for more information."]
       (clojure.string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (clojure.string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn run-hs300s
  [data-path summary-path dt]
  (while (and (not (utils/check-finish data-path dt))
              (<= (Integer. (utils/get-hour)) 22))
    (log/info "check finish! NO FINISH:" data-path dt)
    (Thread/sleep 60000))
  (if (utils/check-finish data-path dt)
    (let [_ (log/info "FINISH check")
          summary (analysis/summary-one-day data-path dt)]
      (log/info "FINISH summary one day")
      (utils/write-summary-one-day summary-path dt summary)
      (log/info "finish write summary"))
    (let [errors (str data-path "/" dt " NOT EXISTS!")]
      (log/error errors)
      (exit 1 errors))))

(defn run-one
  [data-path summary-path code start-dt end-dt big-amount big-volume]
  (log/info data-path summary-path code start-dt end-dt big-amount big-volume)
  (analysis/prn-analysis-one-some-days (str data-path "/" code)
                                       start-dt
                                       end-dt
                                       big-amount
                                       big-volume))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (log/info options)
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))

    (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
    (doseq [component (-> args
                          mount/start-with-args
                          :started)]
      (log/info component "started"))
    
    ;; Execute program with options
    (let [data-path (:stock-data-path env)
          summary-path (:stock-summary-data-path env)
          dt (:dt options)]
      (log/info "data path:" data-path)
      (log/info "summary path:" summary-path)
      (case (first arguments)
        "hs300s" (run-hs300s data-path summary-path dt)
        "one" (run-one data-path summary-path (:code options) (:start-dt options) (:end-dt options) (:big-amount options) (:big-volume options))
        (exit 1 (usage summary))))))
