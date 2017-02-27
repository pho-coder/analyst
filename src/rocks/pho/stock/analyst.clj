(ns rocks.pho.stock.analyst
  (:require [mount.core :as mount]
            [clojure.tools.logging :as log]

            [rocks.pho.stock.analyst.config :refer [env]]
            [rocks.pho.stock.analyst.utils :as utils])
  (:gen-class))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn run [dt]
  (log/info "HI"))

(defn start-app [args]
  (doseq [component (-> args
                        mount/start-with-args
                        :started)]
    (log/info component "started"))
  (log/info env)
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-app))
  (let [dt (utils/get-today-date)]
    (while (and (run dt)
                (<= (Integer. (utils/get-hour)) 20))
      (log/info "run one time")
      (Thread/sleep 600000))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (start-app args))
