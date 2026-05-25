(ns nil.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [nil.spec :as spec]
            [nil.registry :as reg]
            [nil.runtime :as runtime]
            [nil.verify :as verify]))

(defn load-spec [path]
  (let [data (edn/read-string (slurp path))]
    (spec/validate-spec! data)
    (reg/register-spec! data)))

(defn load-specs [dir]
  (let [files (->> (file-seq (io/file dir))
                   (filter #(and (.isFile %) (.endsWith (.getName %) ".clj"))))]
    (vec (map #(load-spec (.getPath %)) files))))

(defn feature [spec]
  (runtime/make-callable spec))

(defn verify [id]
  (when-let [spec (reg/lookup-spec id)]
    (verify/verify-spec spec)))

(defn verify-all []
  (verify/verify-all))
