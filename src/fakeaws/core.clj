(ns fakeaws.core
  (:require [amazonica.aws.sqs :as sqs]
            [clojure.string :refer [upper-case]])
  (:import [io.findify.sqsmock SQSService]
           [org.gaul.s3proxy S3Proxy]
           [org.jclouds.blobstore BlobStoreContext]
           [org.jclouds ContextBuilder]
           [java.util Properties]
           [java.net URI]
           [org.eclipse.jetty.util.component AbstractLifeCycle])
  (:gen-class))


(def aws-credentials
  {:access-key "open"
   :secret-key "sesame"})

(defn start-sqs
  []
  (let [sqs-port  8001 ;; sadly, sqsmock does not allow the port number to be changed
        account   1
        sqs-creds (assoc aws-credentials :endpoint (format "http://localhost:%s" sqs-port))]
    (println "====> SQS mock credentials: " sqs-creds)
    (let [sqs-service (.start (SQSService. sqs-port account))
          queues      {:stockpile.notify (:queue-url (sqs/create-queue sqs-creds :queue-name "stockpile.notify"))
                       :stockpile.fail   (:queue-url (sqs/create-queue sqs-creds :queue-name "stockpile.fail"))
                       :gottfried.notify (:queue-url (sqs/create-queue sqs-creds :queue-name "gottfried.notify"))
                       :gottfried.fail   (:queue-url (sqs/create-queue sqs-creds :queue-name "gottfried.fail"))
                       :us_cfr.notify    (:queue-url (sqs/create-queue sqs-creds :queue-name "us_cfr.notify"))
                       :us_cfr.fail      (:queue-url (sqs/create-queue sqs-creds :queue-name "us_cfr.fail"))
                       :annotator.notify (:queue-url (sqs/create-queue sqs-creds :queue-name "annotator.notify"))}
          queues-vec (-> queues vec)]
      (dotimes [i (count queues-vec)]
        (println (format "\t- %s: [%s]"
                         (-> (get queues-vec i) first name upper-case)
                         (second (get queues-vec i))))))))

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

(defn -main
  [& args]
  (let [sqs-service (start-sqs)
        s3-service  (start-s3)]
    (while true
      (Thread/sleep 10000))))
