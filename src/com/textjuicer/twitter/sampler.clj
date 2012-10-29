(ns com.textjuicer.twitter.sampler
  (:use
   [clojure.java.io :only (writer)]
   [clojure.tools.cli :only (cli)]
   [cheshire.core :only (generate-stream parse-string)]
   [com.textjuicer.twitter.credentials :only (read-credentials)]
   [twitter.callbacks.handlers :only (exception-print response-return-everything)]
   [twitter.api.streaming :only (statuses-sample)])
  (:require
   [clojure.string :as str]
   [http.async.client :as ac]
   [com.textjuicer.twitter.protocols])
  (:import com.textjuicer.twitter.protocols.AsyncStreamingCallback)
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

(defn- on-tweet
  "Helper to create a callback to process a tweet."
  [f]
  (fn [r baos]
    (let
        [code (:code (ac/status r))]
      (cond
       ;; only valid response are processed
       (and (>= code 200) (< code 300)) (f (-> baos str (parse-string true)))

       ;; errors should abort the stream
       (>= code 400) :abort

       ;; ignore other kind of messages
       :else nil))))

(defn stop-after
  "A callback returning :abort after n tweets are processed."
  [n]
  (let [calls (atom 0)]
    (fn [tweet]
      (swap! calls inc)
      (when (>= @calls n) :abort))))

(defn write-json
  "A callback encoding tweets as json on out"
  [out]
  #(generate-stream % out))

(defn report-progress
  "Reports the number of tweets that have been processed on *out*."
  [n]
  (let [progress (atom 0)]
    (fn [_]
      (swap! progress inc)
      (when (< 0.1 (rand))
        (print "\r" @progress "tweets"))
      (when (= @progress n)
        (println)))))

(defn download-tweets
  "Download tweets and pass them as arguments to all the supplied
  callbacks f. Tweets are downloaded until one callback
  returns :abort."
  [credentials & f]

  ;; the client must be close to ensure its thread-pool is freed
  (with-open [client (ac/create-client :request-timeout -1 :follow-redirect false)]
    (let
        [;; identify original tweet (returns false for deleted tweet,
         ;; retweet, ...)
         tweet? #(:id %)

         ;; call all callbacks passed in parameters and return :abort
         ;; when at least one of them returns :abort
         process-tweet #(when (tweet? %)
                          (some #{:abort} (doall ((apply juxt f) %))))

         callback (AsyncStreamingCallback.
                   (on-tweet process-tweet)
                   #(printerr (response-return-everything % :to-json? false))
                   #(binding [*out* *err*] (exception-print %1 %2)))

         ;; open the stream with twitter
         response (statuses-sample :oauth-creds credentials
                                   :callbacks callback
                                   :client client)]

      ;; wait until one callback returns :abort and then close the stream
      (ac/await response))))

(defn -main
  "Small CLI application to download tweets in a file."
  [& argv]
  (let [[options args banner]
        (cli argv
             ["-c" "--credentials" "File containing twitter API credentials."
              :default nil]
             ["-h" "--help" "Print this online help" :flag true :default false]
             ["-n" "--size" "Number of tweets to download."
              :default 1000 :parse-fn #(Integer. %)])
        credentials (:credentials options)
        size (:size options)
        output (first args)]

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
         (download-tweets creds
                          (write-json out)
                          (report-progress size)
                          (stop-after size)))
       (printerr "Invalid credentials"))))
  nil)