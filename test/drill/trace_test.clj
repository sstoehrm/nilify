(ns drill.trace-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [drill.trace :as trace]
            [drill.generator :as gen]
            [drill.registry :as reg]))

(defn- capture-stderr [f]
  (let [sw (java.io.StringWriter.)]
    (binding [*err* sw]
      (f))
    (str sw)))

(deftest emit-off-suppresses-all
  (testing "events are suppressed when *level* is :off"
    (let [out (capture-stderr
               #(binding [trace/*level* :off]
                  (trace/emit :info {:stage :prompt-built :id :test :prompt-length 100})))]
      (is (= "" out)))))

(deftest emit-info-shows-info-events
  (testing "info events appear when *level* is :info"
    (let [out (capture-stderr
               #(binding [trace/*level* :info]
                  (trace/emit :info {:stage :prompt-built :id :test :prompt-length 100})))]
      (is (str/includes? out "[drill/info]"))
      (is (str/includes? out ":test"))
      (is (str/includes? out "100")))))

(deftest emit-info-hides-debug-events
  (testing "debug events are hidden when *level* is :info"
    (let [out (capture-stderr
               #(binding [trace/*level* :info]
                  (trace/emit :debug {:stage :block-extracted :id :test :block-length 50})))]
      (is (= "" out)))))

(deftest emit-debug-shows-full-map
  (testing "debug level prints the full event map"
    (let [out (capture-stderr
               #(binding [trace/*level* :debug]
                  (trace/emit :debug {:stage :block-extracted :id :test :block-length 50})))]
      (is (str/includes? out "[drill/debug]"))
      (is (str/includes? out ":block-length")))))

(deftest emit-debug-shows-info-events
  (testing "info events also appear at debug level"
    (let [out (capture-stderr
               #(binding [trace/*level* :debug]
                  (trace/emit :info {:stage :llm-call-start :id :test})))]
      (is (str/includes? out "[drill/info]"))
      (is (str/includes? out ":test")))))

(deftest format-summary-per-stage
  (testing "each stage produces a meaningful summary line"
    (let [cases [{:stage :prompt-built :id :x :prompt-length 500}
                 {:stage :llm-call-start :id :x}
                 {:stage :llm-call-done :id :x :elapsed-ms 1234 :response-length 800}
                 {:stage :block-extracted :id :x :block-length 420}
                 {:stage :file-written :id :x :path "drill_generated/x.clj" :spec-hash "abc"}
                 {:stage :smoke-pass :id :x :example-count 3}
                 {:stage :produce-prompt-built :id :x :tag :translate :prompt-length 200}
                 {:stage :produce-llm-call-start :id :x :tag :translate}
                 {:stage :produce-llm-call-done :id :x :tag :translate :elapsed-ms 500}
                 {:stage :produce-result :id :x :tag :translate :result "ok"}
                 {:stage :input-validated :id :x :tag :eval :input [:eval "1"]}
                 {:stage :output-validated :id :x :tag :eval :output 1.0}]]
      (doseq [evt cases]
        (let [out (capture-stderr
                   #(binding [trace/*level* :info]
                      (trace/emit :info evt)))]
          (is (str/includes? out (name (:stage evt)))
              (str "missing stage name in output for " (:stage evt))))))))

(deftest gen-feature-emits-trace-events
  (testing "gen-feature! emits expected trace events at :info level"
    (let [events (atom [])
          spec {:id :traced
                :kind :feature
                :lang :babashka
                :desc "test"
                :cases {:eval {:input [:tuple [:= :eval] :string]
                               :output :double
                               :examples [{:in [:eval "(+ 1 2)"] :out 3.0}]}}}]
      (binding [trace/*level* :info
                gen/*generated-dir* "test/tmp"
                gen/*llm-call* (fn [_]
                                 (str "```clojure\n"
                                      "(ns drill-generated.traced\n"
                                      "  (:require [drill.registry :as reg]))\n"
                                      "(defn -impl [[tag & args]]\n"
                                      "  (case tag :eval (Double/parseDouble (str (load-string (first args))))))\n"
                                      "(reg/register-impl! :traced -impl)\n"
                                      "```"))]
        (let [out (capture-stderr
                   #(do (reg/clear!)
                        (reg/register-spec! spec)
                        (gen/gen-feature! spec)))]
          (is (str/includes? out "prompt-built"))
          (is (str/includes? out "llm-call-start"))
          (is (str/includes? out "llm-call-done"))
          (is (str/includes? out "file-written"))
          (is (str/includes? out "smoke-pass"))
          (reg/clear!))))))
