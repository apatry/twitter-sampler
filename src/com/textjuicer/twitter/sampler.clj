(ns com.textjuicer.twitter.sampler
  (:use
   [clojure.set :only (subset?)]
   [clojure.tools.cli :only (cli)]
   [cheshire.core :only (generate-stream parse-string)]
   [twitter.oauth :only (make-oauth-creds)]
   [twitter.callbacks.handlers :only (exception-print response-return-everything)]
   [twitter.api.streaming :only
    (statuses-sample)])
  (:require
   [http.async.client :as ac])
  (:import
   (twitter.callbacks.protocols AsyncStreamingCallback))
  (:gen-class))

(def ^:dynamic *credentials* "Credentials to use for twitter." nil)

(defn stop-after
  "A callback returning :abort after n tweets are processed."
  [n]
  (let [calls (atom 0)]
    (fn [tweet] 
      (swap! calls inc)
      (when (>= @calls n) :abort))))

(defn- valid-credentials?
  "Predicates checking if credentials are valid."
  [c]
  (subset? #{:consumer-key :consumer-secret :access-token :access-token-secret} 
           (set (keys c))))

(defn- make-credentials
  "Create oauth-credentials from a map containing twitter credentials."
  [c]
  {:pre [(valid-credentials? c)]}
  (make-oauth-creds (:consumer-key c)
                    (:consumer-secret c)
                    (:acces-token c)
                    (:access-token-secret c)))

(defn- on-tweet
  "Helper to create a callback to process a tweet."
  [f]
  (fn [r baos]
    (let
        [code (:code (ac/status r))]
      ;; only valid response are processed
      (when  (and (>= code 200) (< code 300))
        (f (-> baos str (parse-string true)))))))

(defn download-tweets
  "Download tweets and pass them as arguments to all the supplied callbacks f. Tweets are downloaded until one callback returns :abort."
  [& f]
  (let 
      [;; a promise that is realised when downloads should abort
       abort (promise)

       ;; identify original tweet (returns false for deleted tweet,
       ;; retweet, ...)
       tweet? #(:id %)

       ;; call all callback and returns :abort when at least one of
       ;; them returns :abort
       process-tweet #(when (tweet? %) 
                        (some #{:abort} (doall ((apply juxt f) %))))

       ;; this callback will store each tweet in "tweets"
       callback (AsyncStreamingCallback.
                 ;; call every callbacks and abort downloads if one of
                 ;; them returns :abort
                 (on-tweet 
                  (fn [tweet] 
                    (when (= :abort (process-tweet tweet))
                      (deliver abort true))))
                 
                 ;; on error, print the whole response and abort
                 (fn [response]
                   (binding [*out* *err*]
                     (-> response 
                         (response-return-everything :to-json? false) 
                         println))
                   (deliver abort true))

                 ;; on exception, print the exception and abort
                 (fn [response throwable] 
                   (binding [*out* *err*]
                     (exception-print response throwable) 
                     (deliver abort true)))) 

       ;; open the stream with twitter
       response (statuses-sample :oauth-creds  (make-credentials *credentials*)
                                 :callbacks callback

                                 ;; default request-timeout is not
                                 ;; enough, we change it
                                 :client (ac/create-client
                                          :request-timeout -1
                                          :follow-redirect false))]

    ;; wait until one callback returns :abort and then close the stream
    @abort
    ((:cancel (meta response)))))

(defn load-credentials
  "Load credentials from file f."
  [f]
  (let
      [content (slurp f)]
    (try
      (read-string content)
      (catch Exception e 
        (throw (IllegalArgumentException. (str "Invalid credentials " content) e))))))

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
        tweets (atom [])]
    (cond
     (:help options)
     (do
       (println "Download random tweets from twitter.")
       (println banner))

     (not credentials)
     (printerr "Credentials are required.")     

     (not-empty args)
     (printerr "Too many arguments.")

     :else
     (binding [*credentials* (load-credentials credentials)]
       (if (valid-credentials? *credentials*)
         (do
           (printlog "Downloading tweets")
           (download-tweets #(swap! tweets conj %) 
                            (stop-after size))
           (printlog "Saving tweets")
           (generate-stream @tweets *out*)
           (flush))
         (printerr "Invalid credentials"))))

    ;; needed to avoid waiting around a minute for the program to end
    (shutdown-agents)
    (System/exit 0)
))