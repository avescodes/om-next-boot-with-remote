(ns repro.app
  (:require [cljs.pprint :refer [pprint]]
            [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer-macros [defui]]
            [cognitect.transit :as transit]
            [sablono.core :refer [html]]))

;; First things first is the on-change handler. Here, we submit two events to transact:
;;
;;  1. A perform-query mutation (incl. the event value)
;;  2. A :search/results read (which will cause a remote read)
;;
;; The latter force a remote read for :search/results (defined below in `read`)
(defn search-on-change [reconciler event]
  (let [query (.. event -target -value)]
    (om/transact! reconciler `[(repro.app/perform-query ~{:query query})
                               :search/results])))

;; A simple component that displays search query and results
;; as well as implementing live search
(defui LiveSearch
  static om/IQuery
  (query [_] [:search/query
              :search/results])

  Object
  (render
   [this]
   (let [{:keys [:search/query
                 :search/results]} (om/props this)]
     (html [:div.search
            [:input {:type :text
                     :on-change (partial search-on-change this)
                     :placeholder "Search"
                     :value query}]
            [:hr]
            [:p ":search/query"]
            [:pre (pr-str query)]
            [:p ":search/results"]
            [:pre (pr-str results)]]))))


;; ## Read & Mutate definitions

(defmulti read om/dispatch)

(defmethod read :default
  [{:keys [state] :as _env} k _params]
  {:value (get @state k "")})

(defn- bounce? [query]
  (or (empty? query)
      (<= (count query) 2)))

(defmethod read :search/results                               ;; #- `read :search/results` implements our remote search
  [{:keys [ast state] :as env} k _params]
  (let [{:keys [:search/query :search/results]} @state]       ;; # - Fetch the current query and results
    (if-not (bounce? query)                                   ;; # - Check for a debouncable query
      {:value (if (not-empty results) results "Searching...") ;; # -   If none, return current results or search placeholder
       :remote (assoc ast :params {:query query})}            ;; # -            and return a modified ast for :remote
      {:value (get @state k "")})))                           ;; # -   Otherwise, just return the current state

(defmulti mutate om/dispatch)

(defmethod mutate 'repro.app/perform-query                    ;; # - The on-change mutation actually does very little
  [{:keys [state] :as _env} _k params]
  (let [{:keys [query]} params]
    {:action #(swap! state assoc :search/query query)         ;; # - It updates the internal query state
     :value {:keys [:search/query]}}))                        ;; # - and provides a hint for a :search/query refresh (works for query, not results?)


;; Remote setup and app initialization

(defn- send
  "Submit a query to a remote server. Use by specifying `:remote` key (the om
  default remote)"
  [{query :remote} cb]
  (console.log "sending query ->" (pr-str query))
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


(def reconciler
  (om/reconciler
    {:state {:search/results ""
             :search/query ""}
     :send send
     :parser (om/parser {:read read
                         :mutate mutate
                         :remotes [:remote]})}))

(defn init []
  (om/add-root! reconciler
                LiveSearch
                (gdom/getElement "app")))



