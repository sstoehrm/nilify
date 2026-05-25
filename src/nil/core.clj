(ns nil.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [nil.spec :as spec]
            [nil.validate :as validate]))

(defn load-spec [path]
  (let [data (edn/read-string (slurp path))]
    (spec/validate-spec! data)))

(defn load-system [path]
  (let [data (edn/read-string (slurp path))]
    (spec/validate-system! data)))

(defn- load-dir [dir loader]
  (let [files (->> (file-seq (io/file dir))
                   (filter #(and (.isFile %) (.endsWith (.getName %) ".clj"))))]
    (vec (map #(loader (.getPath %)) files))))

(defn validate [{:keys [features systems]}]
  (let [feature-map (into {} (map (juxt :id identity) features))
        example-results (vec (map validate/check-examples features))
        connection-results (vec (for [sys (or systems [])]
                                  (validate/check-connections sys feature-map)))]
    {:example-results example-results
     :connection-results connection-results}))

(defn validate-all [features-dir systems-dir]
  (let [features (load-dir features-dir load-spec)
        systems  (if (.isDirectory (io/file systems-dir))
                   (load-dir systems-dir load-system)
                   [])]
    (validate {:features features :systems systems})))
