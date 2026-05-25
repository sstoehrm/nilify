(ns nil.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [nil.spec :as spec]))

(defn load-spec [path]
  (let [data (edn/read-string (slurp path))]
    (spec/validate-spec! data)))
