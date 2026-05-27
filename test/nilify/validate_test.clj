(ns nilify.validate-test
  (:require [clojure.test :refer [deftest is testing]]
            [nilify.validate :as validate]))

(deftest examples-conforming-to-schemas
  (testing "returns :pass when all examples match their schemas"
    (let [spec {:id :math
                :cases {:add {:input [:map [:a :int] [:b :int]]
                              :output [:map [:sum :int]]
                              :examples [{:in {:a 1 :b 2} :out {:sum 3}}
                                         {:in {:a 0 :b 0} :out {:sum 0}}]}}}
          result (validate/check-examples spec)]
      (is (= :pass (:status result)))
      (is (= 2 (count (:results result)))))))

(deftest example-input-violates-schema
  (testing "returns :fail when example input doesn't match input schema"
    (let [spec {:id :bad
                :cases {:x {:input [:map [:a :int]]
                            :output :any
                            :examples [{:in {:a "not-int"} :out nil}]}}}
          result (validate/check-examples spec)]
      (is (= :fail (:status result)))
      (is (= :input-mismatch (:failure (first (:results result))))))))

(deftest example-output-violates-schema
  (testing "returns :fail when example output doesn't match output schema"
    (let [spec {:id :bad
                :cases {:x {:input :any
                            :output [:map [:r :int]]
                            :examples [{:in nil :out {:r "not-int"}}]}}}
          result (validate/check-examples spec)]
      (is (= :fail (:status result)))
      (is (= :output-mismatch (:failure (first (:results result))))))))

(deftest spec-with-no-examples-passes
  (testing "returns :pass when no examples exist"
    (let [spec {:id :empty :cases {:x {:input :any :output :any}}}
          result (validate/check-examples spec)]
      (is (= :pass (:status result)))
      (is (zero? (count (:results result)))))))

(deftest compatible-connection-passes
  (testing "returns :pass when output schema is compatible with input schema"
    (let [features {:translate {:id :translate
                                :cases {:translate {:input [:map [:query :string]]
                                                    :output [:map [:expr :string]]}}}
                    :compute   {:id :compute
                                :cases {:eval {:input [:map [:expr :string]]
                                               :output [:map [:result :double]]}}}}
          system {:id :calc
                  :components {:translate {:feature :translate :lang :python}
                               :compute   {:feature :compute   :lang :babashka}}
                  :connections [[[:translate :translate :output] [:compute :eval :input]]]}
          result (validate/check-connections system features)]
      (is (= :pass (:status result))))))

(deftest incompatible-connection-fails
  (testing "returns :fail when output schema doesn't match input schema"
    (let [features {:translate {:id :translate
                                :cases {:translate {:input [:map [:query :string]]
                                                    :output [:map [:expr :int]]}}}
                    :compute   {:id :compute
                                :cases {:eval {:input [:map [:expr :string]]
                                               :output [:map [:result :double]]}}}}
          system {:id :calc
                  :components {:translate {:feature :translate :lang :python}
                               :compute   {:feature :compute   :lang :babashka}}
                  :connections [[[:translate :translate :output] [:compute :eval :input]]]}
          result (validate/check-connections system features)]
      (is (= :fail (:status result)))
      (is (= :incompatible (:failure (first (:results result))))))))

(deftest system-with-no-connections-passes-validation
  (testing "returns :pass when system has no connections"
    (let [features {:x {:id :x :cases {:a {:input :any :output :any}}}}
          system {:id :s :components {:x {:feature :x :lang :go}}}
          result (validate/check-connections system features)]
      (is (= :pass (:status result)))
      (is (zero? (count (:results result)))))))

(deftest connection-references-missing-feature
  (testing "returns :fail when connection references a feature not in the features map"
    (let [features {}
          system {:id :s
                  :components {:x {:feature :x :lang :go}}
                  :connections [[[:x :a :output] [:y :b :input]]]}
          result (validate/check-connections system features)]
      (is (= :fail (:status result)))
      (is (= :missing-feature (:failure (first (:results result))))))))
