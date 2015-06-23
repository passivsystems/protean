(ns protean.core.generation.generate
  "Simple generative capability - includes simulation testing map making facilities etc."
  (:import java.lang.Math
           (java.util Random UUID)
           org.databene.benerator.primitive.RegexStringGenerator
           org.databene.benerator.engine.DefaultBeneratorContext))

(def rnd (Random.))

(defn rnd-int [] (Math/abs (.nextInt rnd)))

(defn rnd-long [] (Math/abs (.nextLong rnd)))

(defn rnd-double [] (.nextDouble rnd))

(defn rnd-bool [] (.nextBoolean rnd))

(defn rnd-uuid [] (UUID/randomUUID))

(def ascii (map char (range 65 (+ 65 26))))

(defn rnd-str [sz alph] (apply str (repeatedly sz #(rand-nth alph))))

(def rnd-sym #(symbol (rnd-str %1 %2)))

(def rnd-key #(keyword (rnd-str %1 %2)))

(defn rnd-vec [& generators] (into [] (map #(%) generators)))

(defn rnd-map [sz kgen vgen] (into {} (repeatedly sz #(rnd-vec kgen vgen))))

(defn generate [regex]
  (let [generator (RegexStringGenerator. regex)]
    (.init generator (DefaultBeneratorContext.))
    (.generate generator)))
