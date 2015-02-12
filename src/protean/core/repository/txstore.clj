(ns protean.core.repository.txstore
  "A file appending transactional store."
  (:require [clojure.java.io :refer [file reader writer]])
  (:import java.util.Date java.io.PushbackReader))

(defprotocol TX (update [_ state]))

(defrecord SaveEvt [k evt]
  TX
  (update [this state] (update-in state [k] conj evt)))

(defprotocol TXLog (record [_ tx]))

(defrecord DefaultTXLog [ag]
  TXLog
  (record [this tx]
    (send-off ag
      (fn [out] (binding [*out* out *flush-on-newline* true] (io! (prn tx)) out)))))

(defn txs [f]
  (if-not (.exists (file f)) []
    (let [rdr (PushbackReader. (reader f))]
      (take-while identity (repeatedly (fn [] (read rdr false nil)))))))

(defrecord Store [state delegate]
  TXLog
  (record [this tx]
    (dosync
     (alter state (partial update tx))
     (record delegate tx))))

(defn store [f]
   (Store.
     (ref (reduce (fn [state tx] (update tx state)) {} (txs (file f))))
     (DefaultTXLog. (agent (writer (file f) :append true)))))

(defn save-evt [store k evt]
  (let [inst-evt (assoc evt :instant (Date.))]
    (record store (SaveEvt. k inst-evt))
    inst-evt))

(defn save-evts
  "Save all events of a given category.
   k is a key to help with filtering later on, ie :tests :thingy."
  [store k evts] (for [e evts] (save-evt store k e)))
