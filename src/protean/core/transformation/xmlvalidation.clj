(ns protean.core.transformation.xmlvalidation)

(import 'javax.xml.XMLConstants)
(import 'org.xml.sax.SAXException)
(import 'javax.xml.validation.SchemaFactory)
(import 'java.io.File)
(import 'java.io.StringReader)
(import 'javax.xml.transform.stream.StreamSource)

(defn- validator [schema]
  (.newValidator
    (.newSchema
      (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
      (StreamSource. (File. schema)))))

(defn validate [schema data]
  (try
    (.validate (validator schema)
      (StreamSource. (StringReader. data)))
      true
    (catch SAXException e false)))

