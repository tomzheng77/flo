(ns flo.server.codec
  (:import (java.util Base64)
           (javax.crypto SecretKeyFactory)
           (javax.crypto.spec PBEKeySpec)))

(defn base64-encode [to-encode]
  (or (and (string? to-encode) (base64-encode (.getBytes to-encode)))
      (and (bytes? to-encode) (.encodeToString (Base64/getEncoder) to-encode))))

(defn hash-password [password]
  (let [password-bytes (or (and (bytes? password) password) (.getBytes password))
        salt (byte-array 0)
        iterations 10000
        key-length 512
        skf (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA512")
        spec (new PBEKeySpec password-bytes salt iterations key-length)
        key (.generateSecret skf spec)
        result (.getEncoded key)]
    (base64-encode result)))
