(ns nil.core
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [malli.core :as m]
            [malli.error :as me]
            [nil.spec :as spec]
            [nil.validate :as validate]))

;; --- Registry ---

(defonce specs (atom {}))
(defonce implementations (atom {}))
(defonce state (atom {:main-fn nil}))

(defn register-impl! [id impl-fn]
  (swap! implementations assoc id impl-fn)
  impl-fn)

(defn clear! []
  (reset! specs {})
  (reset! implementations {})
  (reset! state {:main-fn nil}))

;; --- Runtime dispatch ---

(defn- validate-io! [schema value error-type id tag]
  (when-not (m/validate schema value)
    (throw (ex-info (str (name error-type) ": " id "/" tag)
                    {:type error-type :id id :tag tag :value value
                     :error (me/humanize (m/explain schema value))}))))

(defn- dispatch [{:keys [id cases]} tag args]
  (let [case-spec (get cases tag)]
    (when-not case-spec
      (throw (ex-info (str "unknown-case: " id "/" tag)
                      {:type :nil/unknown-case :id id :tag tag})))
    (let [packed (vec (cons tag args))
          impl-fn (get @implementations id)]
      (validate-io! (:input case-spec) packed :nil/input-invalid id tag)
      (when-not impl-fn
        (throw (ex-info (str "impl-missing: " id)
                        {:type :nil/impl-missing :id id})))
      (let [result (impl-fn packed)]
        (validate-io! (:output case-spec) result :nil/output-invalid id tag)
        result))))

;; --- Runtime API ---

(defn prompt [& lines]
  (string/join "\n" lines))

(defn feature [the-spec]
  (spec/validate-spec! the-spec)
  (swap! specs assoc (:id the-spec) the-spec)
  (fn [tag & args] (dispatch the-spec tag args)))

(defn produce [the-spec]
  (spec/validate-spec! the-spec)
  (swap! specs assoc (:id the-spec) the-spec)
  (fn [tag & args] (dispatch the-spec tag args)))

(defn system [config f]
  (f))

(defn reg-main [main-fn]
  (swap! state assoc :main-fn main-fn))

(defn main [& args]
  (when-let [f (:main-fn @state)]
    (apply f args)))

;; --- Validation API ---

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
