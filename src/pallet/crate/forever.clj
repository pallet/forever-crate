(ns pallet.crate.forever
  "Crate for forever installation and configuration.

https://github.com/nodejitsu/forever"
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :as logging]
   [pallet.stevedore :as stevedore])
  (:use
   [pallet.action.exec-script :only [exec-checked-script]]
   [pallet.action.package :only [packages]]
   [pallet.common.context :only [throw-map]]
   [pallet.core :only [server-spec]]
   [pallet.parameter :only [assoc-target-settings get-target-settings]]
   [pallet.phase :only [phase-fn]]
   [pallet.thread-expr :only [when-> apply-map->]]
   [pallet.utils :only [apply-map]]
   [pallet.version-dispatch
    :only [defmulti-version-crate defmulti-version defmulti-os-crate
           multi-version-session-method multi-version-method
           multi-os-session-method]]
   [pallet.versions :only [version-string as-version-vector]]))

;;; ## forever install
(def ^:dynamic *forever-defaults*
  {:version "0.9.2"})

;;; Based on supplied settings, decide which install strategy we are using
;;; for forever.

(defmulti-version-crate forever-version-settings [version session settings])

(multi-version-session-method
    forever-version-settings {:os :linux}
    [os os-version version session settings]
  (cond
    (:strategy settings) settings
    (:npm settings) (assoc settings :strategy :npm)
    :else (assoc settings :strategy :npm :npm nil)))

(multi-version-session-method
    forever-version-settings {:os :ubuntu :os-version [12]}
    [os os-version version session settings]
  (cond
    (:strategy settings) settings
    (:npm settings) (assoc settings :strategy :npm)
    :else (assoc settings
            :strategy :npm
            :npm {:packages {:aptitude ["libssl0.9.8"]}})))

;;; ## Settings
(defn- settings-map
  "Dispatch to either openjdk or oracle settings"
  [session settings]
  (forever-version-settings
   session
   (as-version-vector (:version settings))
   (merge *forever-defaults* settings)))

(defn forever-settings
  "Capture settings for forever
:version
:download
:deb"
  [session {:keys [version download deb instance-id]
            :or {version (:version *forever-defaults*)}
            :as settings}]
  (let [settings (settings-map session (merge {:version version} settings))]
    (assoc-target-settings session :forever instance-id settings)))

;;; # Install

;;; Dispatch to install strategy
(defmulti install-method (fn [session settings] (:strategy settings)))

(defmethod install-method :npm
  [session {:keys [version npm]}]
  (->
   session
   (when-> (:packages npm)
     (apply-map-> packages (:packages npm)))
   (exec-checked-script
    "Install forever"
    (npm install (str "forever@" ~version) -g --quiet -y))))

(defn install-forever
  "Install forever. By default will build from source."
  [session & {:keys [instance-id]}]
  (let [settings (get-target-settings
                  session :forever instance-id ::no-settings)]
    (logging/debugf "install-forever settings %s" settings)
    (if (= settings ::no-settings)
      (throw-map
       "Attempt to install forever without specifying settings"
       {:message "Attempt to install forever without specifying settings"
        :type :invalid-operation})
      (install-method session settings))))

;;; # Forever based service
(defn forever-index [script]
  (stevedore/script
   (pipe
    (forever list)
    (awk (str "'{ if ( $5 ~ /"
              ~script
              "/ ) { print substr( $2, 2, length( $2 ) - 2 ) } }'")))))

(defn forever-service
  "Operate a service via forever:

:user   the user to run under
:dir    the working directory for the code
:action either :start or :stop"
  [session script & {:keys [action max dir instance-id script-name user]
                     :or {action :start max 1 script-name "forever script"}}]
  (let [{:keys [home user] :as settings}
        (get-target-settings session :vblob instance-id ::no-settings)]
    (case action
      :start
      (exec-checked-script
       session
       (str "Starting " script-name)
       ~(if user
          (stevedore/script
           (sudo -n -H -u ~user sh -c
                 (quoted
                  (do
                    "("
                    ~(if dir (stevedore/script (cd ~dir)) "")
                    (forever -m ~max start ~script)
                    ")"))))
          (stevedore/script
           "("
           ~(if dir (stevedore/script (cd ~dir)) "")
           (forever -m ~max start ~script)
           ")")))
      :stop
      (exec-checked-script
       session
       (str "Stopping " script-name)
       ~(if user
          (stevedore/script
           (sudo -n -H -u ~user sh -c (quoted
                                       (pipe ~(forever-index script)
                                             (xargs forever stop)))))
          (stevedore/script
           (pipe ~(forever-index script)
                 (xargs forever stop))))))))

;;; # Server spec
(defn forever
  "Returns a service-spec for installing forever."
  [settings]
  (server-spec
   :phases {:settings (phase-fn (forever-settings settings))
            :configure (phase-fn (install-forever))}))
