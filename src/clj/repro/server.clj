(ns repro.server
  (:require [clojure.string :as str]
            [cognitect.transit :as transit]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.http-response :refer [ok header resource-response] :as resp]
            [bidi.ring :refer [make-handler]]
            )
  (:import [java.io ByteArrayOutputStream]))

(def ^:private transit #{"application/transit+msgpack"
                         "application/transit+json;q=0.9"})

(defn- transit-write [clj-obj]
  (let [out-stream (ByteArrayOutputStream.)]
    (transit/write (transit/writer out-stream :json) clj-obj)
    (.toString out-stream)))

(def search-list #{"fooson" "barton" "bazzle"})

(defn search-for [query]
  (if (not-empty query)
    (some #(when (str/starts-with? % query) %) search-list)
    ""))

(defn fake-om-query [om-query]
  (println "Received Query <--" om-query)
  (let [[attr {:keys [query]}] (first om-query)
        result {:search/results (search-for query)}]
    (println "Sending result -->" result)
    result))

(defn- content-type [cnt ctype]
  (let [c (get {:html    "text/html; charset=UTF-8"
                :css     "text/css; charset=UTF-8"
                :js      "application/javascript; charset=UTF-8"
                :json    "application/json; charset=UTF-8"
                :transit "application/transit+json; charset=UTF-8"
                :font    "font/opentype"
                :svg     "image/svg+xml"} ctype
               (name ctype))]
    (resp/content-type cnt c)))

(defn- om-query-resource [req]
  (let [query (transit/read (transit/reader (:body req) :json))
        result (fake-om-query query)]
    (-> result
        transit-write
        ok
        (content-type :transit))))


(def handler
  (make-handler ["/om" {:post om-query-resource}]))

(def app
  (-> handler
      (wrap-resource "")))
