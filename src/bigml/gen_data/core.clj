(ns bigml.gen-data.core
  (:import (java.util Random))
  (:require (clojure.data [csv :as csv])
            (clojure.java [io :as io])
            (bigml.sampling [simple :as simple]
                            [random :as random]))
  (:gen-class))

(def ^:private translation-scale 8)

(defn- seed->rng [seed]
  (random/create :seed seed))

(defn- rng->seed! [rng]
  (random/next-double! rng))

(defn- guassian-generator [seed]
  (let [^Random rng (seed->rng seed)]
    #(.nextGaussian rng)))

(defn- uniform-generator [seed]
  (let [rng (seed->rng seed)]
    #(dec (* 2 (random/next-double! rng)))))

(defn- take1! [coll rng]
  (coll (random/next-int! rng (count coll))))

(def base-generators
  [guassian-generator
   ;; uniform-generator
   ])

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
    (fn []
      (let [^doubles row (double-array cols)]
        (dotimes [i cols]
          (aset-double row i ((gens i))))
        row))))

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
  (let [tot-cols (+ obs-cols hid-cols)
        gen (class-generator classes obs-cols tot-cols seed)]
    (with-open [out-file (io/writer file)]
      (csv/write-csv out-file (repeatedly rows #(gen))))))

(defn -main
  [& {:as params}]
  (let [params (or params {})
        file (params "-file")
        rows (Integer/valueOf (params "-rows" 10000))
        classes (Integer/valueOf (params "-classes" 10))
        fields (Integer/valueOf (params "-fields" 20))
        hidden-fields (Integer/valueOf (params "-hidden-fields" 0))
        seed (params "-seed" "default")
        file (or file
                 (str "c" classes
                      "f" fields
                      "h" hidden-fields
                      "r" rows
                      "-" seed
                      ".csv"))]
    (println "Generating data with:")
    (println "   -file" file)
    (println "   -rows"  rows)
    (println "   -classes" classes)
    (println "   -fields" fields)
    (println "   -hidden-fields" hidden-fields)
    (println "   -seed" seed)
    (time (gen-csv file classes fields hidden-fields rows seed))))
