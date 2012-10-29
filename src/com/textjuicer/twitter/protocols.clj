(ns com.textjuicer.twitter.protocols
  (:use
   [twitter.callbacks.handlers :only (handle-response)])
  (:require
    [http.async.client.request :as req])
   (:import
    [twitter.callbacks.protocols AsyncSyncStatus SingleStreamingStatus EmitCallbackList]))

;; A streaming callback that closes its stream its :part callback returns :abort
(defrecord AsyncStreamingCallback [on-bodypart on-failure on-exception]
  AsyncSyncStatus (get-async-sync [_] :async)
  SingleStreamingStatus (get-single-streaming [_] :streaming)

  EmitCallbackList
  (emit-callback-list
    [this]
    (merge req/*default-callbacks*
           {:completed (fn [response] (handle-response response
                                                       this
                                                       :events #{:on-failure}))
            :part (fn [response baos]
                    (if (= :abort ((:on-bodypart this) response baos))
                      [baos :abort]
                      [baos :continue]))
            :error (fn [response throwable]
                     ((:on-exception this) response throwable) throwable)})))