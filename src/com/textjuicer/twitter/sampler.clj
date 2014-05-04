(ns com.textjuicer.twitter.sampler
  (:use
   [clojure.java.io :only (reader writer)]
   [clojure.tools.cli :only (cli)]
   [com.textjuicer.twitter.credentials :only (read-credentials)]
   [twitter.callbacks.handlers :only (exception-print response-return-everything)]
   [twitter.api.streaming :only (statuses-filter statuses-sample)])
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [cheshire.core :as json]
   [http.async.client :as ac]
   [com.textjuicer.twitter.protocols])
  (:import 
   com.textjuicer.twitter.protocols.AsyncStreamingCallback
   [java.io InputStreamReader PipedInputStream PipedOutputStream PushbackReader])
  (:gen-class))

(defn- printlog
  "Print logging messages on *err*"
  [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn- printerr
  "Print error messages on *err*"
  [& args]
  (binding [*out* *err*]
    (apply println args)))

(defn- write-tweet
  "A callback writing tweets to out"
  [out]
  (fn [r baos]
    (let
        [code (:code (ac/status r))]
      (cond
       ;; only valid response are processed
       (and (>= code 200) (< code 300)) (try 
                                          (.write out (.toByteArray baos))
                                          (catch java.io.InterruptedIOException e
                                            ;; stream has been closed on the other side
                                                  :abort))

       ;; errors should abort the stream
       (>= code 400) :abort

       ;; ignore other kind of messages
       :else (printerr "Received " ac/status " from server.")))))

(defn download-tweets
  "Download tweets and pass them as arguments to all the supplied
  callbacks (list of functions). Tweets are downloaded until one callback
  returns :abort."
  [api credentials size out & {:keys [params proxy]}]

  ;; the client must be close to ensure its thread-pool is freed
  (with-open [twitter-out (PipedOutputStream.)
              in (InputStreamReader. (PipedInputStream. twitter-out) "UTF-8")
              client (ac/create-client :request-timeout -1 
                                       :follow-redirect false
                                       :proxy proxy)]
    (let
        [;; identify original tweet (returns false for deleted tweet,
         ;; retweet, ...)
         tweet? #(% "id")

         callback (AsyncStreamingCallback.
                   (write-tweet twitter-out)
                   #(printerr (response-return-everything % :to-json? false))
                   #(binding [*out* *err*] (exception-print %1 %2)))

         ;; open the stream with twitter
         response (api :oauth-creds credentials
                       :callbacks callback
                       :client client
                       :params params)]

      ;; wait until one callback returns :abort and then close the stream
      (doseq [[i tweet] (map-indexed vector (take size (filter tweet? (json/parsed-seq in))))]
        (print (inc i) " tweets\r")
        (json/generate-stream tweet out))
      (println))))

(defn read-edn
  "Read an edn datastructure from a file."
  [f]
  (with-open [rdr (PushbackReader. (reader f))]
    (edn/read rdr)))

(defn -main
  "Small CLI application to download tweets in a file."
  [& argv]
  (let [[options args banner]
        (cli argv
             ["-c" "--credentials" "File containing twitter API credentials."
              :default nil]
             ["-h" "--help" "Print this online help" :flag true :default false]
             ["-n" "--size" "Number of tweets to download."
              :default 1000 :parse-fn #(Integer. %)]
             ["-p" "--proxy" "Proxy configuration file."]
             ["-t" "--track" "Keywords, mentions or hashtags to track (separated by comma)"])
        credentials (:credentials options)
        size (:size options)
        track (:track options)
        output (first args)
        proxy-file (:proxy options)
        proxy (when proxy-file (read-edn proxy-file))]

    (cond
     (:help options)
     (do
       (println "Download random tweets from twitter.")
       (println banner))

     (not credentials)
     (printerr "Credentials are required.")

     (nil? output)
     (printerr "One output file is required.")

     (> (count args) 1)
     (printerr "Too many arguments.")

     :else
     (if-let [creds (read-credentials credentials)]
       (with-open [out (writer output)]
         (when proxy
           (println "Downloading using proxy " proxy)
           (println "Invalid proxy parameters may make the connection hangs."))
         (if track
           (download-tweets statuses-filter creds size out :proxy proxy :params {:track track})
           (download-tweets statuses-sample creds size out :proxy proxy)))
       (printerr "Invalid credentials"))))
  nil)