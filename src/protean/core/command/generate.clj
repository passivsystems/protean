(ns protean.core.command.generate
  "Generate values for placeholders, only when integration testing."
  (:refer-clojure :exclude [long int])
  (:require [clojure.data.generators :as gen])
  (:import java.lang.Math))

(defn int
  "Generate a random int.
   For some reason generators int does not return an int."
  [] (.intValue (gen/uniform Integer/MIN_VALUE (inc Integer/MAX_VALUE))))

(defn int+ [] (Math/abs (int (gen/int))))

(defn long+ [] (Math/abs (gen/long)))
