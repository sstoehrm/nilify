(ns drill.core
  (:refer-clojure :exclude [list])
  (:require [clojure.java.io :as io]
            [clojure.string  :as string]
            [drill.spec      :as spec]
            [drill.registry  :as reg]
            [drill.runtime   :as runtime]
            [drill.produce   :as produce]
            [drill.drift     :as drift]
            [drill.generator :as generator]
            [drill.trace     :as trace]))

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

(defn- file-content [id]
  (let [f (io/file (generated-path id))]
    (when (.exists f) (slurp f))))

(defn list []
  (vec (for [s (vals @reg/descriptions)]
         {:id     (:id s)
          :kind   (:kind s)
          :status (case (:kind s)
                    :produce :n/a
                    :feature (drift/status s (file-content (:id s))))})))

(defn check []
  (let [items (list)]
    (doseq [i items]
      (println (format "%-30s %-10s %s" (:id i) (:kind i) (:status i))))
    (when (some #(not (contains? #{:fresh :n/a} (:status %))) items)
      (System/exit 1))))

(defn regen [id]
  (when-let [the-spec (reg/lookup-spec id)]
    (swap! reg/state assoc :generate? true)
    (try
      (generator/gen-feature! the-spec)
      (finally
        (swap! reg/state assoc :generate? false)))))

(defn regen-stale []
  (vec (for [item (list)
             :when (contains? #{:stale :missing} (:status item))]
         (regen (:id item)))))

(defn regen-all []
  (vec (for [item (list)
             :when (= :feature (:kind item))]
         (regen (:id item)))))

(defn diff [id]
  (let [the-spec (reg/lookup-spec id)
        existing (file-content id)
        new-hash (drift/spec-hash the-spec)
        old-hash (drift/header-hash existing)]
    {:id id :old-hash old-hash :new-hash new-hash :would-regen? (not= old-hash new-hash)}))

(defn main [& args]
  (let [argset (set args)
        level  (cond
                 (contains? argset "--debug")   :debug
                 (contains? argset "--verbose") :info
                 :else                          :off)]
    (binding [trace/*level* level]
      (cond
        (contains? argset "--list")
        (doseq [i (list)] (println i))

        (contains? argset "--check")
        (check)

        (contains? argset "--regen-all")
        (do (swap! reg/state assoc :generate? true)
            (try
              (doseq [r (regen-all)] (println r))
              (finally
                (swap! reg/state assoc :generate? false))))

        (or (contains? argset "--generate")
            (contains? argset "--regen-stale"))
        (do (swap! reg/state assoc :generate? true)
            (try
              (doseq [r (regen-stale)] (println r))
              (finally
                (swap! reg/state assoc :generate? false))))

        :else
        (when-let [f (:main-fn @reg/state)]
          (apply f args))))))
