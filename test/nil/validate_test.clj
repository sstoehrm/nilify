(ns nil.validate-test
  (:require [clojure.test :refer [deftest is testing]]
            [nil.validate :as validate]))

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
