(ns nil.spec-test
  (:require [clojure.test :refer [deftest is]]
            [nil.spec :as spec]))

(deftest nil-namespace-works
  (is (= :it-works (spec/hello))))
