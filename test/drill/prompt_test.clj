(ns drill.prompt-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [drill.prompt :as prompt]))

(def spec
  {:id :compute
   :lang :babashka
   :desc "Eval Clojure"
   :cases {:eval {:input  [:tuple [:= :eval] :string]
                  :output :double
                  :desc   "Eval one expression"}}})

(def siblings
  [{:id :other :desc "Does other things"
    :cases {:do {:input [:tuple [:= :do] :string] :output :string}}}])

(deftest prompt-mentions-spec-id
  (is (str/includes? (prompt/build spec []) ":compute")))

(deftest prompt-mentions-lang
  (is (str/includes? (prompt/build spec []) ":babashka")))

(deftest prompt-mentions-each-case
  (is (str/includes? (prompt/build spec []) ":eval")))

(deftest prompt-lists-siblings-when-present
  (is (str/includes? (prompt/build spec siblings) ":other")))

(deftest prompt-omits-sibling-section-when-empty
  (is (not (str/includes? (prompt/build spec []) "Sibling features"))))

(deftest prompt-tells-claude-no-header
  (is (str/includes? (prompt/build spec []) "do NOT include the AUTO-GENERATED header")))
