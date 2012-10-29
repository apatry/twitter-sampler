(ns com.textjuicer.twitter.credentials
  (:use
   [clojure.java.io :only (reader)]
   [clojure.set :only (subset?)]
   [twitter.oauth :only (make-oauth-creds)])
  (:import
   [java.io PushbackReader]))

(defn- valid-credentials?
  "Predicates checking if credentials are valid."
  [c]
  (subset? #{:consumer-key :consumer-secret :access-token :access-token-secret} 
           (set (keys c))))

(defn- make-credentials
  "Create oauth-credentials from a map containing twitter credentials."
  [c]
  (when (valid-credentials? c)
    (make-oauth-creds (:consumer-key c)
                      (:consumer-secret c)
                      (:access-token c)
                      (:access-token-secret c))))

(defn read-credentials
  "Reads oauth credentials from f, which should be a valid parameter
  for clojure.java.io/reader."  
  [f]
  (with-open [input (PushbackReader. (reader f))]
    (-> input read make-credentials)))