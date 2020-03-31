(ns util.crypto)

(defn md5 [^String data]
  (let [crypto (js/require "crypto")]
    (-> crypto (.createHash "md5") (.update data) (.digest "hex"))))
