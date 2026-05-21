(ns drill.core
  (:refer-clojure :exclude [list])
  (:require [clojure.java.io :as io]
            [clojure.string  :as string]
            [drill.spec      :as spec]
            [drill.registry  :as reg]
            [drill.runtime   :as runtime]
            [drill.produce   :as produce]
            [drill.drift     :as drift]
            [drill.generator :as generator]))

(defn prompt [& lines]
  (string/join "\n" lines))

(defn- generated-path [id]
  (str generator/*generated-dir* "/"
       (-> id name (string/replace "-" "_")) ".clj"))

(defn- ensure-impl-loaded! [id]
  (when-not (reg/lookup id)
    (let [f (io/file (generated-path id))]
      (when (.exists f) (load-file (.getPath f))))))

(defn feature [the-spec]
  (spec/validate-spec! the-spec)
  (reg/register-spec! (assoc the-spec :kind :feature))
  (if (:generate? @reg/state)
    (runtime/make-stub the-spec)
    (do
      (ensure-impl-loaded! (:id the-spec))
      (runtime/make-callable the-spec))))

(defn produce [the-spec]
  (spec/validate-spec! the-spec)
  (reg/register-spec! (assoc the-spec :kind :produce))
  (if (:generate? @reg/state)
    (runtime/make-stub the-spec)
    (produce/make-callable the-spec)))

(defn reg-main [main-fn]
  (swap! reg/state assoc :main-fn main-fn))
