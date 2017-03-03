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
   ["-c" "--code code"]
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
        "  start    Start a new server"
        "  stop     Stop an existing server"
        "  status   Print a server's status"
        ""
        "Please refer to the manual page for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

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
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)
        data-path (:stock-data-path env)
        summary-path (:stock-summary-data-path env)]
    (when-not (nil? errors)
      (log/error )
      (System/exit 1))
    (log/info "data path:" data-path)
    (log/info "summary path:" summary-path)
    (let [tp (:type options)]
      (when (= tp "hs300s")
        (log/info "type:" tp "dt:" (:dt options))
        (while (and (not (utils/check-finish data-path (:dt options)))
                    (<= (Integer. (utils/get-hour)) 22))
          (log/info "check finish! NO FINISH")
          (Thread/sleep 60000))
        (log/info "FINISH check")
        (let [summary (analysis/summary-one-day data-path (:dt options))]
          (log/info "FINISH summary one day")
          (utils/write-summary-one-day summary-path (:dt options) summary)
          (log/info "finish write summary")))
      (when (= tp "one")
        (log/info "type:" tp "code:" (:code options))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      (not= (count arguments) 1) (exit 1 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Execute program with options
    (case (first arguments)
      "hs300s" (run-hs300s options)
      "one" (run-one options)
      (exit 1 (usage summary)))))
