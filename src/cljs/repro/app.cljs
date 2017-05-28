(ns repro.app
  (:require [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer-macros [defui]]
            [cognitect.transit :as transit]
            [sablono.core :refer [html]]))



(defui App
  Object
  (render [this]
    (let [{:keys [greeting]} (om/props this)]
      (dom/h1 nil greeting))))

(defui LiveSearch
  static om/IQuery
  (query [_] '[:search/results])

  Object
  (render
   [this]
   (let [{:keys [:search/results]} (om/props this)]
     (html [:div.search
            [:p ":search/results"]
            [:pre (pr-str results)]]))
   ))

;; Read & Mutate definitions

(defmulti read om/dispatch)

(defmethod read :search/results
  [{:keys [ast state] :as env}
   k
   params]
  {:value "Searching..."
   :remote true})

(defmulti mutate om/dispatch)

(defmethod mutate 'perform-query
  [env k params]
  {:action #(println "In 'perform-query action")
   :value {}})

;; Remote setup and app initialization

(defn- send [{query :remote} cb]
  (console.log "sending query...")
  (let [xhr                     (new js/XMLHttpRequest)
        request-body            (transit/write (transit/writer :json) query)]
    (.open xhr "POST" "/om")
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

(defn logging-merge [reconciler state novelty query]
  (console.log "Current state" state)
  (console.log "Received Novelty" novelty)
  (om/default-merge reconciler state novelty query))

(def reconciler
  (om/reconciler
    {:state {:greeting "Hello world!"
             :search/results ""
             :search/query "Foo"}
     :send send
     :merge logging-merge
     :parser (om/parser {:read read
                         :mutate mutate
                         :remotes [:remote]})}))

(defn init []
  (om/add-root! reconciler
                LiveSearch
                (gdom/getElement "app")))



