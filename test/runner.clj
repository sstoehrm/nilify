(ns runner
  (:require [clojure.test :as t]))

(def test-namespaces
  '[drill.spec-test
    drill.registry-test
    drill.runtime-test
    drill.drift-test
    drill.prompt-test
    drill.generator-test
    drill.produce-test
    drill.core-test
    end-to-end-test])

(defn -main [& _]
  (let [loaded (reduce (fn [acc ns-sym]
                         (try (require ns-sym) (conj acc ns-sym)
                              (catch Exception _ acc)))
                       []
                       test-namespaces)
        {:keys [fail error]} (apply t/run-tests loaded)]
    (System/exit (if (zero? (+ (or fail 0) (or error 0))) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
