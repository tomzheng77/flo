(ns flo.server.codec
  (:import (java.util Base64)
           (javax.crypto SecretKeyFactory)
           (javax.crypto.spec PBEKeySpec)))

; converts a string or byte array into a Base64 string
(defn base64-encode [to-encode]
  (or (and (string? to-encode) (base64-encode (.getBytes to-encode)))
      (and (bytes? to-encode) (.encodeToString (Base64/getEncoder) to-encode))))

; hashes a password string into a Base64 string
(defn hash-password [password]
  (let [password-chars (or (and (bytes? password) password) (.toCharArray password))
        salt (.getBytes "salt")
        iterations 10000
        key-length 512
        skf (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA512")
        spec (new PBEKeySpec password-chars salt iterations key-length)
        key (.generateSecret skf spec)
        result (.getEncoded key)]
    (base64-encode result)))
