(ns textjuicer.twitter.corpus
  (:use
   [clojure.tools.logging :only (info)]
   [twitter.oauth :only (make-oauth-creds)]
   [twitter.callbacks.handlers :only (exception-print response-throw-error)]
   [twitter.api.streaming :only (statuses-sample)])
  (:require
   [clojure.data.json :as json]
   [http.async.client :as ac])
  (:import
   (twitter.callbacks.protocols AsyncStreamingCallback)))


(def ^:dynamic *creds*
  (make-oauth-creds "pP9aWuBeBXaGONiY5ygmiQ"
                    "GhROeP2JB6gtFUIR9hv2LQ55UR8RCwp1A8YONl0"
                    "144713078-SoIUun0XHQVzWZQpXTvumfSMaxrrJvgaonZ4TnIK"
                    "wWh3JAewl5SwyZC9jRlnT49bwj5o8N4lWaGzYGeh0"))

(defn on-tweet
  "Helper to create a callback to process a tweet."
  [f]
  (fn [r baos] (f (-> baos str json/read-json))))

(defn download-tweets
  "Download at least n random tweets and return them as a seq."
  [n]
  (let
      [tweets (atom [])
       ;; this callback will store each tweet in "tweets"
       callback (AsyncStreamingCallback. (on-tweet #(swap! tweets conj %1))
                                         response-throw-error
                                         exception-print)
       ;; open the stream with twitter
       response (statuses-sample :oauth-creds *creds*
                                 :callbacks callback
                                 ;; default request-timeout is not
                                 ;; enough, we change it
                                 :client (ac/create-client
                                          :request-timeout -1
                                          :follow-redirect false))]
    ;; wait until at least n tweets are stored then stop the stream
    ;; and return the tweets
    (loop
        []
      (if (<  (count @tweets) n)
        (do
          (Thread/sleep 200)
          (info "Downloaded " (count @tweets) " tweets")
          (recur))
        ((:cancel (meta response)))))
    @tweets))
