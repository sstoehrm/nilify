(ns nilify-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]))

(load-file "nilify")

(alias 'cli 'nilify.cli)

(def ^:dynamic *test-dir* "test/tmp/nilify")

(defn- delete-tree [dir]
  (let [d (io/file dir)]
    (when (.exists d)
      (doseq [f (reverse (file-seq d))]
        (.delete f)))))

(use-fixtures :each (fn [f]
                      (delete-tree *test-dir*)
                      (.mkdirs (io/file *test-dir* "nil" "features"))
                      (.mkdirs (io/file *test-dir* "nil" "systems"))
                      (f)))

;; ---- Spec validation ----

(deftest valid-feature-spec-passes
  (testing "a well-formed feature spec passes validation"
    (let [s {:id :compute
             :desc "evaluate expressions"
             :cases {:eval {:input [:map [:expr :string]]
                            :output [:map [:result :double]]
                            :examples [{:in {:expr "(+ 1 2)"} :out {:result 3.0}}]}}}]
      (is (= s (cli/validate-spec! s))))))

(deftest spec-requires-id
  (testing "spec without :id throws"
    (is (thrown-with-msg? Exception #"invalid-spec"
          (cli/validate-spec! {:cases {:x {:input :any :output :any}}})))))

(deftest feature-without-cases-passes
  (testing "feature without :cases is valid"
    (let [s {:id :ui :desc "a user interface"}]
      (is (= s (cli/validate-spec! s))))))

(deftest spec-with-deps-passes
  (testing "spec with :deps validates"
    (let [s {:id :calc :deps [:translate :compute]
             :cases {:x {:input [:map [:q :string]] :output [:map [:r :double]]}}}]
      (is (= s (cli/validate-spec! s))))))

(deftest spec-with-tech-passes
  (testing "spec with :tech validates"
    (let [s {:id :db :tech "sqlite" :desc "database layer"}]
      (is (= s (cli/validate-spec! s))))))

;; ---- System validation ----

(deftest valid-system-spec-passes
  (testing "a well-formed system spec passes validation"
    (let [s {:id :calculator
             :desc "NL calculator"
             :components {:translate {:feature :translate :lang :python}
                          :compute   {:feature :compute   :lang :babashka}}
             :connections [[[:translate :translate :output] [:compute :eval :input]]]}]
      (is (= s (cli/validate-system! s))))))

(deftest system-requires-id
  (testing "system without :id throws"
    (is (thrown-with-msg? Exception #"invalid-system"
          (cli/validate-system! {:components {:x {:feature :x :lang :python}}})))))

(deftest system-requires-components
  (testing "system without :components throws"
    (is (thrown-with-msg? Exception #"invalid-system"
          (cli/validate-system! {:id :broken})))))

(deftest system-component-requires-feature-and-lang
  (testing "component missing :lang throws"
    (is (thrown-with-msg? Exception #"invalid-system"
          (cli/validate-system! {:id :bad :components {:x {:feature :x}}})))))

(deftest system-without-connections-passes
  (testing "system with no connections is valid"
    (let [s {:id :simple :components {:x {:feature :x :lang :go}}}]
      (is (= s (cli/validate-system! s))))))

;; ---- Example conformance ----

(deftest examples-conforming-to-schemas
  (testing "returns :pass when all examples match"
    (let [spec {:id :math
                :cases {:add {:input [:map [:a :int] [:b :int]]
                              :output [:map [:sum :int]]
                              :examples [{:in {:a 1 :b 2} :out {:sum 3}}
                                         {:in {:a 0 :b 0} :out {:sum 0}}]}}}
          result (cli/check-examples spec)]
      (is (= :pass (:status result)))
      (is (= 2 (count (:results result)))))))

(deftest example-input-violates-schema
  (testing "returns :fail on input mismatch"
    (let [result (cli/check-examples
                  {:id :bad :cases {:x {:input [:map [:a :int]]
                                        :output :any
                                        :examples [{:in {:a "not-int"} :out nil}]}}})]
      (is (= :fail (:status result)))
      (is (= :input-mismatch (:failure (first (:results result))))))))

(deftest example-output-violates-schema
  (testing "returns :fail on output mismatch"
    (let [result (cli/check-examples
                  {:id :bad :cases {:x {:input :any
                                        :output [:map [:r :int]]
                                        :examples [{:in nil :out {:r "not-int"}}]}}})]
      (is (= :fail (:status result)))
      (is (= :output-mismatch (:failure (first (:results result))))))))

(deftest spec-with-no-examples-passes
  (testing "returns :pass when no examples exist"
    (let [result (cli/check-examples {:id :empty :cases {:x {:input :any :output :any}}})]
      (is (= :pass (:status result)))
      (is (zero? (count (:results result)))))))

;; ---- Connection compatibility ----

(deftest compatible-connection-passes
  (testing "returns :pass when schemas are compatible"
    (let [features {:translate {:id :translate
                                :cases {:translate {:input [:map [:query :string]]
                                                    :output [:map [:expr :string]]}}}
                    :compute   {:id :compute
                                :cases {:eval {:input [:map [:expr :string]]
                                               :output [:map [:result :double]]}}}}
          system {:id :calc
                  :components {:translate {:feature :translate :lang :python}
                               :compute   {:feature :compute   :lang :babashka}}
                  :connections [[[:translate :translate :output] [:compute :eval :input]]]}]
      (is (= :pass (:status (cli/check-connections system features)))))))

(deftest incompatible-connection-fails
  (testing "returns :fail when schemas are incompatible"
    (let [features {:translate {:id :translate
                                :cases {:translate {:input [:map [:query :string]]
                                                    :output [:map [:expr :int]]}}}
                    :compute   {:id :compute
                                :cases {:eval {:input [:map [:expr :string]]
                                               :output [:map [:result :double]]}}}}
          system {:id :calc
                  :components {:translate {:feature :translate :lang :python}
                               :compute   {:feature :compute   :lang :babashka}}
                  :connections [[[:translate :translate :output] [:compute :eval :input]]]}]
      (is (= :fail (:status (cli/check-connections system features))))
      (is (= :incompatible (:failure (first (:results (cli/check-connections system features)))))))))

(deftest system-with-no-connections-passes-validation
  (testing "returns :pass when no connections"
    (let [result (cli/check-connections
                  {:id :s :components {:x {:feature :x :lang :go}}}
                  {:x {:id :x :cases {:a {:input :any :output :any}}}})]
      (is (= :pass (:status result)))
      (is (zero? (count (:results result)))))))

(deftest connection-references-missing-feature
  (testing "returns :fail for missing feature"
    (let [result (cli/check-connections
                  {:id :s :components {:x {:feature :x :lang :go}}
                   :connections [[[:x :a :output] [:y :b :input]]]}
                  {})]
      (is (= :fail (:status result)))
      (is (= :missing-feature (:failure (first (:results result))))))))

;; ---- CLI integration ----

(deftest validate-from-directories
  (testing "validate loads specs from dirs"
    (spit (str *test-dir* "/nil/features/a.clj")
          (pr-str {:id :a :cases {:x {:input [:map [:v :int]] :output [:map [:v :int]]
                                      :examples [{:in {:v 1} :out {:v 1}}]}}}))
    (spit (str *test-dir* "/nil/systems/s.clj")
          (pr-str {:id :s :components {:a {:feature :a :lang :go}}}))
    (let [results (cli/validate-all (str *test-dir* "/nil/features")
                                    (str *test-dir* "/nil/systems"))]
      (is (= 1 (count (:example-results results))))
      (is (= :pass (:status (first (:example-results results))))))))

(def cli-path (.getAbsolutePath (io/file "nilify")))

(deftest validate-cli-command
  (testing "nilify validate exits 0 on valid specs"
    (spit (str *test-dir* "/nil/features/b.clj")
          (pr-str {:id :b :cases {:x {:input [:map [:v :int]] :output [:map [:v :int]]
                                      :examples [{:in {:v 1} :out {:v 1}}]}}}))
    (let [{:keys [exit out]} (babashka.process/shell
                              {:out :string :err :string :continue true
                               :dir *test-dir*}
                              "bb" cli-path "validate")]
      (is (zero? exit))
      (is (clojure.string/includes? out "1/1 pass")))))
