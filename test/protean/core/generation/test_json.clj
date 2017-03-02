(ns protean.core.generation.test-json
  (:require [cheshire.core :as cc]
            [protean.core.generation.json :refer [gen]]
            [expectations :refer :all]))

(let [json (gen "test-data/schemas/pet.schema.json")]
  (println json))

(let [json (gen "test-data/schemas/test.schema.json")]
  (println (cc/generate-string (cc/parse-string json) {:pretty true})))
