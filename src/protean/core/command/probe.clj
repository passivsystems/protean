(ns protean.core.command.probe
  "Probe polymorphism.")

(defmulti config (fn [command & _] command))

(defmulti build (fn [command & _] command))

(defmulti dispatch (fn [command & _] command))

(defmulti analyse (fn [command & _] command))

