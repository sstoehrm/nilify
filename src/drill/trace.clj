(ns drill.trace
  (:require [clojure.pprint :as pp]))

(def ^:dynamic *level* :off)

(def ^:private level-rank {:off 0 :info 1 :debug 2})

(defn- enabled? [event-level]
  (>= (level-rank *level* 0) (level-rank event-level 0)))

(defn- format-summary [{:keys [stage id tag prompt-length elapsed-ms
                                response-length block-length path
                                spec-hash example-count]}]
  (case stage
    :prompt-built
    (str "prompt-built " id " (" prompt-length " chars)")

    :llm-call-start
    (str "llm-call-start " id)

    :llm-call-done
    (str "llm-call-done " id " (" elapsed-ms "ms, " response-length " chars)")

    :block-extracted
    (str "block-extracted " id " (" block-length " chars)")

    :file-written
    (str "file-written " id " -> " path)

    :smoke-pass
    (str "smoke-pass " id " (" example-count " examples)")

    :produce-prompt-built
    (str "produce-prompt-built " id "/" tag " (" prompt-length " chars)")

    :produce-llm-call-start
    (str "produce-llm-call-start " id "/" tag)

    :produce-llm-call-done
    (str "produce-llm-call-done " id "/" tag " (" elapsed-ms "ms)")

    :produce-result
    (str "produce-result " id "/" tag)

    :input-validated
    (str "input-validated " id "/" tag)

    :output-validated
    (str "output-validated " id "/" tag)

    (str (name stage) " " id)))

(defn emit [level event]
  (when (enabled? level)
    (let [summary (format-summary event)]
      (binding [*out* *err*]
        (println (str "[drill/" (name level) "] " summary))
        (when (= *level* :debug)
          (println (str "  " (with-out-str (pp/pprint event)))))
        (flush)))))
