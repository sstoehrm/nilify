(ns drill.core
  (:require [clojure.string :as string]))

(def ^:private state (atom {:generate? true}))
(def ^:private descriptions (atom {}))
(def ^:private implementations (atom {}))
(def ^:private main-fn (atom {}))

(defn- register [{id :id :as params}]
  (if (get @descriptions id)
    (throw (ex-info "Already registered"))
    (swap! assoc id params)))

(defn- feature- [generate? {:keys [input output lang]
                            :or {input :string
                                 output :string
                                 lang :babashka}
                            :as params}]
  (if generate?
    (register-feature params)
    (get @implementations (:id params))))

(defn feature [params]
  (feature- (:generate? state) params))

(defn- process- [generate? {:keys [input output]
                            :or {input :string output :string}
                            :as params}]
  (if generate?
    (register-feature params)
    (get @implementations (:id params))))

(defn process [params]
  (process- (:generate? state) params))

(defn reg-main [main-fn]
  (swap! state assoc :main-fn main-fn))

;; This means currently it is not repl driven
;; Can fix this later
(defn main [&args]
  (if true;; implement get from args
    (generate-implementations @descriptions)
    ((:main-fn @:main-fn) args)))

;; convinence functions
(defn prompt [& lines]
  (string/join "\n" lines))
