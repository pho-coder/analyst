(ns rocks.pho.stock.analyst
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]
            [clojure.tools.cli :refer [parse-opts]]

            [rocks.pho.stock.analyst.config :refer [env]]
            [rocks.pho.stock.analyst.utils :as utils]
            [rocks.pho.stock.analyst.analysis :as analysis])
  (:gen-class))

(def cli-options
  [["-d" "--dt date" "date"]])

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn start-app [args]
  (doseq [component (-> args
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (log/debug env)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
  (let [opts (parse-opts args cli-options)
        options (:options opts)
        dt (if (contains? options :dt)
             (:dt options)
             (utils/get-today-date))
        data-path (:stock-data-path env)
        summary-path (:stock-summary-data-path env)]
    (when-not (nil? (:errors opts))
      (log/error opts)
      (System/exit 1))
    (while (and (not (utils/check-finish data-path dt))
                (<= (Integer. (utils/get-hour)) 20))
      (log/info "check finish! NO FINISH")
      (Thread/sleep 60000))
    (log/info "FINISH")
    (analysis/summary-oneday data-path summary-path dt)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (start-app args))
