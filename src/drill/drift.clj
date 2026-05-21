(ns drill.drift
  (:require [clojure.walk :as walk])
  (:import [java.security MessageDigest]))

(defn- canonicalize [x]
  (walk/postwalk
   (fn [v] (if (map? v) (into (sorted-map) v) v))
   x))

(defn- sha256-hex [^String s]
  (let [md (MessageDigest/getInstance "SHA-256")
        bs (.digest md (.getBytes s "UTF-8"))]
    (apply str (map #(format "%02x" %) bs))))

(defn spec-hash [spec]
  (-> spec canonicalize pr-str sha256-hex (subs 0 16)))

(defn header-hash [^String file-content]
  (when file-content
    (some->> (re-find #";; spec-hash: ([0-9a-f]+)" file-content)
             second)))

(defn status [spec file-content]
  (cond
    (nil? file-content)                            :missing
    (= (spec-hash spec) (header-hash file-content)) :fresh
    :else                                          :stale))
