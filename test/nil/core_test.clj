(ns nil.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [nil.core :as nil-core]))

(def ^:dynamic *test-dir* "test/tmp/nil-v2")

(defn- delete-tree [dir]
  (let [d (io/file dir)]
    (when (.exists d)
      (doseq [f (reverse (file-seq d))]
        (.delete f)))))

(use-fixtures :each (fn [f]
                      (nil-core/clear!)
                      (delete-tree *test-dir*)
                      (.mkdirs (io/file *test-dir* "features"))
                      (.mkdirs (io/file *test-dir* "systems"))
                      (f)))

(deftest load-feature-spec
  (testing "load-spec reads and validates a feature spec"
    (spit (str *test-dir* "/features/math.clj")
          (pr-str {:id :math
                   :cases {:add {:input [:map [:a :int] [:b :int]]
                                 :output [:map [:sum :int]]
                                 :examples [{:in {:a 1 :b 2} :out {:sum 3}}]}}}))
    (let [spec (nil-core/load-spec (str *test-dir* "/features/math.clj"))]
      (is (= :math (:id spec))))))

(deftest load-system-spec
  (testing "load-system reads and validates a system spec"
    (spit (str *test-dir* "/systems/calc.clj")
          (pr-str {:id :calc
                   :components {:math {:feature :math :lang :python}}
                   :connections []}))
    (let [sys (nil-core/load-system (str *test-dir* "/systems/calc.clj"))]
      (is (= :calc (:id sys))))))

(deftest validate-checks-examples
  (testing "validate returns example conformance results"
    (let [spec {:id :math
                :cases {:add {:input [:map [:a :int]]
                              :output [:map [:sum :int]]
                              :examples [{:in {:a 1} :out {:sum 2}}]}}}
          results (nil-core/validate {:features [spec]})]
      (is (= 1 (count (:example-results results))))
      (is (= :pass (:status (first (:example-results results))))))))

(deftest validate-checks-connections
  (testing "validate returns connection compatibility results"
    (let [f1 {:id :translate
              :cases {:translate {:input [:map [:q :string]]
                                  :output [:map [:expr :string]]}}}
          f2 {:id :compute
              :cases {:eval {:input [:map [:expr :string]]
                             :output [:map [:r :double]]}}}
          sys {:id :calc
               :components {:translate {:feature :translate :lang :python}
                            :compute   {:feature :compute   :lang :babashka}}
               :connections [[[:translate :translate :output] [:compute :eval :input]]]}
          results (nil-core/validate {:features [f1 f2] :systems [sys]})]
      (is (= 1 (count (:connection-results results))))
      (is (= :pass (:status (first (:connection-results results))))))))

(deftest validate-all-from-directories
  (testing "validate-all loads from dirs and validates everything"
    (spit (str *test-dir* "/features/a.clj")
          (pr-str {:id :a :cases {:x {:input [:map [:v :int]] :output [:map [:v :int]]
                                      :examples [{:in {:v 1} :out {:v 1}}]}}}))
    (spit (str *test-dir* "/systems/s.clj")
          (pr-str {:id :s
                   :components {:a {:feature :a :lang :go}}}))
    (let [results (nil-core/validate-all (str *test-dir* "/features")
                                         (str *test-dir* "/systems"))]
      (is (= 1 (count (:example-results results)))))))

;; --- Runtime API tests ---

(deftest feature-returns-callable
  (testing "feature registers spec and returns a callable"
    (let [spec {:id :math
                :cases {:add {:input [:tuple [:= :add] :int :int]
                              :output :int}}}
          f (nil-core/feature spec)]
      (nil-core/register-impl! :math (fn [[_tag a b]] (+ a b)))
      (is (= 5 (f :add 2 3))))))

(deftest produce-returns-callable
  (testing "produce registers spec and returns a callable"
    (let [spec {:id :echo
                :cases {:echo {:input [:tuple [:= :echo] :string]
                               :output :string}}}
          f (nil-core/produce spec)]
      (nil-core/register-impl! :echo (fn [[_tag s]] s))
      (is (= "hello" (f :echo "hello"))))))

(deftest system-calls-function
  (testing "system executes the provided function"
    (let [called (atom false)]
      (nil-core/system {:id :test :lang :babashka}
                       (fn [] (reset! called true)))
      (is @called))))

(deftest reg-main-and-main
  (testing "reg-main registers a main fn, main calls it"
    (let [result (atom nil)]
      (nil-core/reg-main (fn [& args] (reset! result args)))
      (nil-core/main "a" "b")
      (is (= '("a" "b") @result)))))

(deftest feature-throws-on-missing-impl
  (testing "calling a feature without a registered impl throws"
    (let [spec {:id :noimpl
                :cases {:x {:input [:tuple [:= :x]] :output :any}}}
          f (nil-core/feature spec)]
      (is (thrown-with-msg? Exception #"impl-missing"
            (f :x))))))
