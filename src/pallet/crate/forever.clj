(ns pallet.crate.forever
  "Crate for forever installation and configuration.

https://github.com/nodejitsu/forever

With forever, there is no configuration file, so we
abuse the service name to pass the executable script."
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :refer [debugf warn]]
   [pallet.actions :refer [exec-checked-script package plan-when]]
   [pallet.api :as api :refer [plan-fn]]
   [pallet.crate
    :refer [assoc-settings defmethod-plan get-settings target-flag?]]
   [pallet.crate-install :as crate-install]
   [pallet.crate.service
    :refer [service-supervisor service-supervisor-available?
            service-supervisor-config]]
   [pallet.script.lib :refer [has-command?]]
   [pallet.stevedore :as stevedore]
   [pallet.utils :refer [apply-map deep-merge]]
   [pallet.version-dispatch
    :refer [defmethod-version-plan defmulti-version-plan]]
   [pallet.versions :refer [version-string as-version-vector]]))

;;; ## forever install
(def ^:dynamic *forever-defaults*
  {:version "0.10.9"})

;;; # Settings
;;;
;;; Use npm to install forever
;;;
;;; Links:
;;; https://github.com/nodejitsu/forever
(defmulti-version-plan default-settings [version])

(defmethod-version-plan
  default-settings {:os :linux}
  [os os-version version]
  {:version (version-string version)
   :install-strategy ::npm
   :packages ["forever"]
   :global true})

;;; ## Settings
(defn settings
  "Capture settings for forever
:version"
  [{:keys [version instance-id]
    :or {version (:version *forever-defaults*)}
    :as settings}]
  (let [settings (deep-merge (default-settings version) settings)]
    (debugf "forever settings %s" settings)
    (assoc-settings :forever settings {:instance-id instance-id})))

;;; # Install

;;; Dispatch to install strategy
(defmethod-plan crate-install/install ::npm
  [facility instance-id]
  (let [{:keys [version] :as settings}
        (get-settings :forever {:instance-id instance-id})]
     (exec-checked-script
      "Install forever"
      (if (~has-command? forever)
        ;; there doesn't seem to be a way of checking the forever version
        (println "forever already installed, skipping")
        ("npm" "--parseable" "--color" false
         install (str "forever@" ~version) -g --quiet -y)))))

(defn install
  "Install forever. By default from npm."
  [{:keys [instance-id]}]
  (crate-install/install :forever instance-id))

(defn forever
  "Return a forever command for the specified arguments"
  [script {:keys [action max env instance-id]
           :or {action :start max 1}}]
  (let [env-string (string/join " " (map (fn [[var val]]
                                           (str (name var) "=\"" val "\""))
                                         env))]
    (case action
      :start (stevedore/fragment
              (~env-string forever --plain -m ~max start ~script))
      :list (stevedore/fragment
              ("forever" --plain list))
      (stevedore/fragment
       ("forever" --plain ~action ~script)))))

;;; # Service Supervisor Implementation
(defmethod service-supervisor-available? :forever
  [_]
  true)

(defmethod service-supervisor-config :forever
  [_
   {:keys [] :as service-options}
   {:keys [instance-id] :as options}]
  (comment "Do nothing, there is no configuration file"))

(defmethod service-supervisor :forever
  [_
   {:keys [service-name]}
   {:keys [action if-flag if-stopped max env script-name instance-id]
    :or {action :start
         script-name service-name}
    :as options}]
  (let []
    (case action
      :enable (comment "Nothing to do here")
      :disable  (comment "Nothing to do here")
      :start-stop (comment "Nothing to do here")
      (if if-flag
        (plan-when (target-flag? if-flag)
          (exec-checked-script
           (str (name action) " " script-name)
           ~(forever service-name (assoc (select-keys options [:max :env])
                                    :action action))))
        (if if-stopped
          (exec-checked-script
           (str (name action) " " service-name)
           (if-not ((pipe
                     ~(forever service-name
                               (assoc (select-keys options [:max :env])
                                 :action :list))
                     ("grep" (quoted  ~service-name))))
             ~(forever service-name (assoc (select-keys options [:max :env])
                                      :action action))))
          (case action
            ;; upstart reports an error if we try starting when already running
            :start
            (exec-checked-script
             (str (name action) " " service-name)
             (if-not ((pipe
                       ~(forever service-name
                                 (assoc (select-keys options [:max :env])
                                   :action :list))
                       ("grep" (quoted ~service-name))))
               ~(forever service-name (assoc (select-keys options [:max :env])
                                        :action action))))

            ;; upstart reports an error if we try stopping when not running
            :stop
            (exec-checked-script
             (str (name action) " " service-name)
             (if ((pipe ~(forever service-name
                                  (assoc (select-keys options [:max :env])
                                    :action :list))
                        ("grep" (quoted ~service-name))))
               ~(forever service-name (assoc (select-keys options [:max :env])
                                        :action action))))

            ;; otherwise, just perform the action
            (exec-checked-script
             (str (name action) " " service-name)
             ~(forever service-name (assoc (select-keys options [:max :env])
                                      :action action)))))))))

;;; # Server spec
(defn server-spec
  "Returns a service-spec for installing forever."
  [{:keys [instance-id] :as settings}]
  (api/server-spec
   :phases {:settings (plan-fn
                        (pallet.crate.forever/settings settings))
            :configure (plan-fn
                         (install {:instance-id instance-id}))}))
