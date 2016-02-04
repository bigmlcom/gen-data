;; Copyright 2016 BigML
;; Licensed under the Apache License, Version 2.0
;; http://www.apache.org/licenses/LICENSE-2.0
(ns bigml.gen-data.core
  (:import (java.util Random))
  (:require (clojure [string :as string])
            (clojure.data [csv :as csv])
            (clojure.java [io :as io])
            (clojure.tools [cli :as cli])
            (bigml.sampling [simple :as simple]
                            [random :as random]))
  (:gen-class))

(def ^:private translation-scale 8)

(defn- seed->rng [seed]
  (random/create :seed seed))

(defn- rng->seed! [rng]
  (random/next-double! rng))

(defn- gaussian-generator [seed]
  (let [^Random rng (seed->rng seed)]
    #(.nextGaussian rng)))

(defn- take1! [coll rng]
  (coll (random/next-int! rng (count coll))))

(def ^:private base-generators
  [gaussian-generator])

(defn- rand-vector! [cols rng]
  (vec (repeatedly cols #(dec (* 2 (random/next-double! rng))))))

(defn- add-projection [cols seed]
  (let [rng (seed->rng seed)
        projs (->> #(into-array Double/TYPE (rand-vector! cols rng))
                   (repeatedly cols)
                   (vec))]
    (fn [^doubles row]
      (let [^doubles nrow (double-array cols)]
        (dotimes [i cols]
          (let [^doubles proj (projs i)
                s (loop [sum 0
                         j 0]
                    (if (< j cols)
                      (recur (+ sum (* (aget proj j) (aget row j)))
                             (inc j))
                      sum))]
            (aset nrow i s)))
        nrow))))

(defn- add-translation [cols seed]
  (let [rng (seed->rng seed)
        ts (mapv #(* translation-scale %)
                 (rand-vector! cols rng))
        ^doubles ts (into-array Double/TYPE ts)]
    (fn [^doubles row]
      (dotimes [i cols]
        (aset-double row i (+ (aget row i) (aget ts i))))
      row)))

(defn- repeated-sample [coll seed]
  (simple/sample coll :seed seed :replace true))

(defn- raw-generator [cols seed]
  (let [rng (seed->rng seed)
        gens (mapv #((take1! base-generators rng) %)
                   (repeatedly cols #(rng->seed! rng)))]
    #(let [^doubles row (double-array cols)]
       (dotimes [i cols]
         (aset-double row i ((gens i))))
       row)))

(defn- combined-generators [size seed]
  (let [rng (seed->rng seed)]
    (comp (add-translation size (rng->seed! rng))
          (add-projection size (rng->seed! rng))
          (raw-generator size (rng->seed! rng)))))

(defn- class-generator [classes obs cols seed]
  (let [rng (seed->rng seed)
        class-generators
        (vec (for [seed (repeatedly classes #(rng->seed! rng))]
               (combined-generators cols seed)))]
    #(let [class (random/next-int! rng classes)
           result (transient [])
           ^doubles full-result ((class-generators class))]
       (dotimes [i obs]
         (conj! result (aget full-result i)))
       (conj! result (str "class" class))
       (persistent! result))))

(defn- gen-csv [file classes obs-cols hid-cols rows seed]
  (let [seed (or seed (rand))
        tot-cols (+ obs-cols hid-cols)
        gen (class-generator classes obs-cols tot-cols seed)]
    (with-open [out-file (io/writer file)]
      (csv/write-csv out-file (repeatedly rows #(gen))))))

(def ^:private cli-options
  [["-o" "--output FILE" "Output file"
    :default "out.csv"
    :parse-fn identity]
   ["-c" "--classes CLASSES" "Number of classes"
    :default 10
    :parse-fn #(Integer/parseInt %)
    :validate [#(and (integer? %) (>= % 0)) "Must be an integer >= 0"]]
   ["-r" "--rows ROWS" "Row size"
    :default 10000
    :parse-fn #(Integer/parseInt %)
    :validate [#(and (integer? %) (pos? %)) "Must be a positive integer"]]
   ["-f" "--fields FIELDS" "Observed numeric fields"
    :default 20
    :parse-fn #(Integer/parseInt %)
    :validate [#(and (integer? %) (pos? %)) "Must be a positive integer"]]
   ["-h" "--hidden HIDDEN" "Hidden numeric fields"
    :default 0
    :parse-fn #(Integer/parseInt %)
    :validate [#(and (integer? %) (>= % 0)) "Must be an integer >= 0"]]
   ["-s" "--seed SEED" "Seed for the RNG"
    :parse-fn identity]])

(defn- usage [options-summary]
  (->> ["Generates randomly projected and translated gaussian clusters."
        ""
        "Usage: lein run [options]"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn- handle-errors [errors]
  (println (str "The following errors occurred while parsing your command:\n"
                (string/join \newline errors)))
  (System/exit 1))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (if errors
      (handle-errors errors)
      (let [{:keys [output rows classes fields hidden seed]} options]
        (time (gen-csv output classes fields hidden rows seed))))))
