(ns repro.server
  (:require [cognitect.transit :as transit]
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

(defn fake-om-query [query]
  (println "request body / %s" query)
  {:value {:search/result "foo"}})

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
