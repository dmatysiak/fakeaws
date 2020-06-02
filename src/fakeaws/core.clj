(ns fakeaws.core
  (:require [amazonica.aws.sqs :as sqs]
            [clojure.string :refer [join upper-case]]
            [clojure.tools.cli :refer [parse-opts]])
  (:import [io.findify.sqsmock SQSService]
           [org.gaul.s3proxy S3Proxy]
           [org.jclouds.blobstore BlobStoreContext]
           [org.jclouds ContextBuilder]
           [java.util Properties]
           [java.net URI]
           [org.eclipse.jetty.util.component AbstractLifeCycle])
  (:gen-class))


(def sqs-port 8001)

(def account  1)

(def aws-credentials
  {:access-key "open"
   :secret-key "sesame"
   :endpoint   (format "http://localhost:%s" sqs-port)})

(def sqs-queues nil)

(defn start-sqs
  []
  (println "====> SQS mock credentials: " aws-credentials)
  (let [sqs-service (.start (SQSService. sqs-port account))
        queues      {:stockpile.notify (:queue-url (sqs/create-queue aws-credentials :queue-name "stockpile.notify"))
                     :stockpile.fail   (:queue-url (sqs/create-queue aws-credentials :queue-name "stockpile.fail"))
                     :gottfried.notify (:queue-url (sqs/create-queue aws-credentials :queue-name "gottfried.notify"))
                     :gottfried.fail   (:queue-url (sqs/create-queue aws-credentials :queue-name "gottfried.fail"))
                     :us_cfr.notify    (:queue-url (sqs/create-queue aws-credentials :queue-name "us_cfr.notify"))
                     :us_cfr.fail      (:queue-url (sqs/create-queue aws-credentials :queue-name "us_cfr.fail"))
                     :annotator.notify (:queue-url (sqs/create-queue aws-credentials :queue-name "annotator.notify"))}
        queues-vec (-> queues vec)]
    (alter-var-root #'sqs-queues (constantly queues))
    (dotimes [i (count queues-vec)]
      (println (format "\t- %s: [%s]"
                       (-> (get queues-vec i) first name upper-case)
                       (second (get queues-vec i)))))))

(defn start-s3
  []
  (let [s3-dir   (str (clojure.java.io/file "/tmp" (str (java.util.UUID/randomUUID))))
        s3-port  8002
        props    (doto (Properties.)
                   (.setProperty "jclouds.filesystem.basedir" s3-dir))
        context  (-> (ContextBuilder/newBuilder "filesystem")
                     (.credentials "identity" "credential")
                     (.overrides props)
                     (.build BlobStoreContext))
        s3-proxy (-> (S3Proxy/builder)
                     (.blobStore (.getBlobStore context))
                     (.endpoint (URI/create (str "http://localhost:" s3-port)))
                     .build)]
    (println "====> S3 mock credentials: " (assoc aws-credentials :endpoint (format "http://localhost:%s" s3-port)))
    (println "\t- S3 DIR: " s3-dir)
    (.start s3-proxy)
    (while (not (-> s3-proxy .getState (.equals AbstractLifeCycle/STARTED)))
      (Thread/sleep 1))))

(def cli-options
  [["-d" "--dequeue NUM_MSGS"
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 %) "Must be a number greater than 0"]]
   ["-m" "--send-msg MSG_STR"]
   ["-q" "--queue QUEUE"]
   ["-s" "--start-aws"]])

(defn -main
  [& args]
  (let [{:keys [options] :as parsed} (parse-opts args cli-options)]
    (cond
      (and (options :dequeue) (options :queue))
      (doall (map #(println (:body %))
                  (:messages (sqs/receive-message
                              aws-credentials
                              :queue-url (format "%s/%s/%s"
                                                 (:endpoint aws-credentials)
                                                 account
                                                 (options :queue))
                              :wait-time-seconds 10
                              :max-number-of-messages (options :dequeue)))))

       (and (options :send-msg) (options :queue))
       (do
         (sqs/send-message aws-credentials (format "%s/%s/%s"
                                                   (:endpoint aws-credentials)
                                                   account
                                                   (options :queue))
                           (options :send-msg))
         (println (str "Sent message to " (options :queue) "!")))

       (options :start-aws)
       (let [sqs-service (start-sqs)
             s3-service  (start-s3)]
         (while true
           (Thread/sleep 10000)))

       :else
       (println parsed))))
