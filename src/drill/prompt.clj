(ns drill.prompt
  (:require [clojure.pprint :as pp]
            [clojure.string :as str]))

(defn- pp-str [x]
  (with-out-str (pp/pprint x)))

(defn- sibling-block [siblings]
  (when (seq siblings)
    (str "Sibling features available (you may require their generated namespaces):\n"
         (str/join "\n"
                   (for [s siblings]
                     (str "- " (:id s) " — " (or (:desc s) "(no description)")))))))

(defn build [spec siblings]
  (str/join
   "\n"
   (remove nil?
           [ "You are generating an implementation file for a drill feature."
             "Follow the conventions described in the drill-feature-author skill."
             ""
             "Feature spec:"
             (str/trim-newline (pp-str spec))
             ""
             (sibling-block siblings)
             ""
             "Output requirements:"
             "- Respond with exactly one ```clojure ... ``` fenced block."
             "- The block must contain a complete (ns drill-generated.<id> ...) form,"
             "  a `-impl` defn that takes the packed [tag & args] tuple,"
             "  and a call to (drill.registry/register-impl! :<id> -impl) at the end."
             "- do NOT include the AUTO-GENERATED header line — drill prepends it."
             "- Do not include explanations outside the code block."])))
