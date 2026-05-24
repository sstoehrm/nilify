(ns nil-beta.init
  (:require [clojure.java.io :as io]))

(def runtime-files
  ["core.clj" "spec.clj" "registry.clj" "runtime.clj" "verify.clj"])

(defn- copy-resource [source-path target-path]
  (let [src (io/file source-path)
        tgt (io/file target-path)]
    (io/make-parents tgt)
    (io/copy src tgt)))

(defn- nil-beta-root []
  (let [f (io/file "src/nil/core.clj")]
    (if (.exists f)
      "."
      (throw (ex-info "cannot find nil-beta source. Run from nil-beta project root."
                      {:type :nil-beta/source-not-found})))))

(defn init! [target-dir]
  (let [root    (nil-beta-root)
        nil-dir (io/file target-dir "nil")]
    (.mkdirs (io/file nil-dir "features"))
    (.mkdirs (io/file nil-dir "generated"))
    (doseq [f runtime-files]
      (copy-resource (str root "/src/nil/" f)
                     (str nil-dir "/" f)))
    (copy-resource (str root "/skills/nil-beta/SKILL.md")
                   (str target-dir "/.claude/skills/nil-beta/SKILL.md"))
    {:target target-dir :status :initialized}))

(defn -main [& args]
  (let [target (or (first args) ".")]
    (println (str "Initializing nil-beta in " target "..."))
    (init! target)
    (println "Done. Created nil/ folder and deployed skill.")))
