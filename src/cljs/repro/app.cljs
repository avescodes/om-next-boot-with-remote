(ns repro.app
  (:require [cljs.pprint :refer [pprint]]
            [goog.dom :as gdom]
            [om.dom :as dom]
            [om.next :as om :refer-macros [defui]]
            [cognitect.transit :as transit]
            [sablono.core :refer [html]]))

(defn search-on-change [reconciler event]
  (let [query (.. event -target -value)]
    (console.log "on-change" query)
    (om/transact! reconciler `[(repro.app/perform-query ~{:query query})
                               :search/query
                               :search/results])))

;; TODO: Does not work
(defn clear-search [reconciler _]
  (console.log "clear")
  (om/transact! reconciler `[(repro.app/clear)
                             :search/query
                             :search/results]))

(defui LiveSearch
  static om/IQuery
  (query [_] '[:search/results :search/query])

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
            [:button {:on-click (partial clear-search this)}
             "Clear"]
            [:hr]
            [:p ":search/query"]
            [:pre (pr-str query)]
            [:p ":search/results"]
            [:pre (pr-str results)]]))))

;; Read & Mutate definitions

(defmulti read om/dispatch)

(defn- bounce? [query]
  (or (empty? query)
      (<= (count query) 2)))

(defmethod read :search/results
  [{:keys [ast state] :as env}
   k
   params]
  (let [{:keys [:search/query :search/results]} @state]
    (if (not-empty results)
      {:value results}                          ;; # - When result is present, display it.
      (if-not (bounce? query)                   ;; # - Otherwise debounce query, fetching from remote
        {:value "Searching..."
         :remote (assoc ast :params {:query query})} ;; # - The Magic
        {:value (get @state k "")}))))

;; Default read to getting the value from the state or an empty string
(defmethod read :default
  [{:keys [state] :as env}
   k
   params]
  (get state k ""))

(defmulti mutate om/dispatch)

(defmethod mutate 'repro.app/clear
  [{:keys [env] :as state} k params]
  {:action #(swap! state assoc :search/results ""
                               :search/query "")})
(defmethod mutate 'repro.app/perform-query
  [{:keys [state] :as env}
   k
   params]
  (let [{:keys [query]} params]
    (console.log "perform-query" query)
    (if (not= query (get @state :search/query))  ;; #- Don't re-run query if it is identical to last query
      ;; New query, assoc to state and invoke a search/results remote read
      {:action #(swap! state assoc :search/query query)
       :value {:keys [:search/results]}}         ;; # - Specify keys for refresh (sp. ask for :search/results read)
      {:action identity})))


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
     :normalize false
     :send send
     :merge om/default-merge
     :parser (om/parser {:read read
                         :mutate mutate
                         :remotes [:remote]})}))

(defn init []
  (om/add-root! reconciler
                LiveSearch
                (gdom/getElement "app")))



