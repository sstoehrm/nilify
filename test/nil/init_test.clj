(ns nil.init-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [nil-beta.init :as init]))

(def ^:dynamic *target* "test/tmp/init-target")

(use-fixtures :each (fn [f]
                      (let [d (io/file *target*)]
                        (when (.exists d)
                          (doseq [f (reverse (file-seq d))]
                            (.delete f))))
                      (.mkdirs (io/file *target*))
                      (spit (str *target* "/bb.edn") (pr-str {:paths ["src"]}))
                      (f)))

(deftest init-creates-nil-directory-structure
  (testing "init creates nil/ with features/ and generated/ subdirs"
    (init/init! *target*)
    (is (.isDirectory (io/file *target* "nil")))
    (is (.isDirectory (io/file *target* "nil" "features")))
    (is (.isDirectory (io/file *target* "nil" "generated")))))

(deftest init-copies-runtime-files
  (testing "init copies runtime .clj files into nil/"
    (init/init! *target*)
    (doseq [f ["core.clj" "spec.clj" "registry.clj" "runtime.clj" "verify.clj"]]
      (is (.exists (io/file *target* "nil" f))
          (str "missing runtime file: " f)))))

(deftest init-deploys-skill
  (testing "init copies the skill into .claude/skills/"
    (init/init! *target*)
    (is (.exists (io/file *target* ".claude" "skills" "nil-beta" "SKILL.md")))))

(deftest init-is-idempotent
  (testing "running init twice doesn't throw"
    (init/init! *target*)
    (init/init! *target*)
    (is (.exists (io/file *target* "nil" "core.clj")))))
