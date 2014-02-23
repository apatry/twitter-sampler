(ns com.textjuicer.twitter.sampler
  (:use
   [clojure.java.io :only (reader writer)]
   [clojure.tools.cli :only (cli)]
   [cheshire.core :only (generate-stream parse-string)]
   [com.textjuicer.twitter.credentials :only (read-credentials)]
   [twitter.callbacks.handlers :only (exception-print response-return-everything)]
   [twitter.api.streaming :only (statuses-sample)])
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [http.async.client :as ac]
   [com.textjuicer.twitter.protocols])
  (:import 
   com.textjuicer.twitter.protocols.AsyncStreamingCallback
   java.io.PushbackReader)
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
  callbacks (list of functions). Tweets are downloaded until one callback
  returns :abort."
  [credentials callbacks & {:keys [proxy]}]

  ;; the client must be close to ensure its thread-pool is freed
  (with-open [client (ac/create-client :request-timeout -1 
                                       :follow-redirect false
                                       :proxy proxy)]
    (let
        [;; identify original tweet (returns false for deleted tweet,
         ;; retweet, ...)
         tweet? #(:id %)

         ;; call all callbacks passed in parameters and return :abort
         ;; when at least one of them returns :abort
         process-tweet #(when (tweet? %)
                          (some #{:abort} (doall ((apply juxt callbacks) %))))

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
             ["-p" "--proxy" "Proxy configuration file."])
        credentials (:credentials options)
        size (:size options)
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
         (let
             [counter (atom 0)]
           (while (< @counter size)
             (when (> @counter 0)
               (println "Connection closed after " @counter " tweets, resuming..."))
             (download-tweets creds
                              [(write-json out)
                               (report-progress size)
                               (stop-after (- size @counter))
                               (fn [tweet] (swap! counter inc))]
                              :proxy proxy))))
       (printerr "Invalid credentials"))))
  nil)