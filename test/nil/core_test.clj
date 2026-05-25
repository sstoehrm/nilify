(ns nil.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [nil.core :as nil-core]
            [nil.registry :as reg]))

(def ^:dynamic *test-dir* "test/tmp/nil-core")

(defn- delete-tree [^java.io.File f]
  (when (.isDirectory f)
    (run! delete-tree (.listFiles f)))
  (.delete f))

(use-fixtures :each (fn [f]
                      (reg/clear!)
                      (delete-tree (io/file *test-dir*))
                      (.mkdirs (io/file *test-dir* "features"))
                      (.mkdirs (io/file *test-dir* "generated"))
                      (f)
                      (reg/clear!)))

(deftest load-spec-from-file
  (testing "load-spec reads EDN from a file and registers the spec"
    (let [path (str *test-dir* "/features/compute.clj")]
      (spit path (pr-str {:id :compute
                          :cases {:eval {:input [:tuple [:= :eval] :string]
                                         :output :double
                                         :examples [{:in [:eval "(+ 1 2)"] :out 3.0}]}}}))
      (let [spec (nil-core/load-spec path)]
        (is (= :compute (:id spec)))
        (is (= spec (reg/lookup-spec :compute)))))))

(deftest load-spec-invalid-throws
  (testing "load-spec throws on invalid spec data"
    (let [path (str *test-dir* "/features/bad.clj")]
      (spit path (pr-str {:not-a-spec true}))
      (is (thrown-with-msg? Exception #"invalid-spec"
            (nil-core/load-spec path))))))

(deftest feature-creates-callable
  (testing "feature returns a callable function with dispatch"
    (let [spec {:id :math
                :cases {:add {:input [:tuple [:= :add] :int :int]
                              :output :int}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :math (fn [[_tag a b]] (+ a b)))
      (let [f (nil-core/feature spec)]
        (is (= 5 (f :add 2 3)))))))

(deftest verify-delegates-to-verify-module
  (testing "verify checks a registered spec by id"
    (let [spec {:id :math
                :cases {:add {:input [:tuple [:= :add] :int :int]
                              :output :int
                              :examples [{:in [:add 1 2] :out 3}]}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :math (fn [[_tag a b]] (+ a b)))
      (let [result (nil-core/verify :math)]
        (is (= :pass (:status result)))))))

(deftest verify-unknown-id-returns-nil
  (testing "verify returns nil for unknown spec id"
    (is (nil? (nil-core/verify :nonexistent)))))

(deftest verify-all-checks-everything
  (testing "verify-all returns results for all specs"
    (reg/register-spec! {:id :a :cases {:x {:input [:tuple [:= :x]] :output :any}}})
    (reg/register-spec! {:id :b :cases {:y {:input [:tuple [:= :y]] :output :any}}})
    (reg/register-impl! :a (fn [_] nil))
    (reg/register-impl! :b (fn [_] nil))
    (let [results (nil-core/verify-all)]
      (is (= 2 (count results))))))

(deftest load-all-specs-from-directory
  (testing "load-specs reads all .clj files from a features directory"
    (spit (str *test-dir* "/features/a.clj")
          (pr-str {:id :a :cases {:x {:input [:tuple [:= :x]] :output :any}}}))
    (spit (str *test-dir* "/features/b.clj")
          (pr-str {:id :b :cases {:y {:input [:tuple [:= :y]] :output :any}}}))
    (let [specs (nil-core/load-specs (str *test-dir* "/features"))]
      (is (= 2 (count specs)))
      (is (= #{:a :b} (set (map :id specs)))))))
