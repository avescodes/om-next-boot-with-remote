(ns repro.app
  (:require [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer-macros [defui]]
            [cognitect.transit :as transit]
            [sablono.core :refer [html]]))

(def app-state (atom {:greeting "Hello world!"
                      :search/results ""
                      :search/query "Foo"}))

(defui App
  Object
  (render [this]
    (let [{:keys [greeting]} (om/props this)]
      (dom/h1 nil greeting))))

(defmulti read om/dispatch)

(defmethod read :search/results
  [{:keys [ast state] :as env}
   k
   params]
  {:value {:search/results "Searching"}
   :remote true})

(defui LiveSearch
  static om/IQuery
  (query [_] '[])

  Object
  (render
   [this]

   ))

;; Remote setup and app initialization

(defn- send [{:query remote} cb]
  (console.log "sending query...")
  (let [xhr                     (new js/XMLHttpRequest)
        request-body            (transit/write (transit/writer :json) query)]
    (.open xhr "POST" "/")
    (.setRequestHeader xhr "Content-Type" "application/transit+json")
    (.setRequestHeader xhr "Accept" "application/transit+json")
    (.addEventListener
      xhr "load"
      (fn [evt]
        (let [status (.. evt -currentTarget -status)]
          (case status
            200 (let [response (transit/read (transit/reader :json)
                                             (.. evt -currentTarget -responseText))]
                  (cb response query))
            (js/alert (str "Error: Unexpected status code: " status
                           ". Please screenshot and contact an engineer."))))))
    (.send xhr request-body)))

(def reconciler
  (om/reconciler
    {:state app-state
     :send   send
     :parser (om/parser {:read read
                         :mutate om/default-mutate
                         :remotes [:remote]})}))
(defn init []
  (om/add-root! reconciler
                App
                (gdom/getElement "app")))



