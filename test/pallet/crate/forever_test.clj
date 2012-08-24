(ns pallet.crate.forever-test
  (:use
   [pallet.action :only [def-clj-action]]
   [pallet.action.exec-script :only [exec-checked-script exec-script]]
   [pallet.action.package :only [install-deb package]]
   [pallet.action.remote-file :only [remote-file]]
   [pallet.build-actions :only [build-actions]]
   [pallet.core :only [lift]]
   [pallet.crate.automated-admin-user :only [automated-admin-user]]
   [pallet.crate.network-service :only [wait-for-http-status]]
   [pallet.crate.node-js :only [install-nodejs nodejs-settings]]
   [pallet.node :only [primary-ip]]
   [pallet.parameter :only [get-target-settings]]
   [pallet.parameter-test :only [settings-test]]
   [pallet.phase :only [phase-fn]]
   [pallet.session :only [nodes-in-group]]
   clojure.test
   pallet.crate.forever
   pallet.test-utils)
  (:require
   [clojure.tools.logging :as logging]
   [pallet.live-test :as live-test]
   [pallet.stevedore :as stevedore]))

(deftest invoke-test
  (is (build-actions
       {}
       (forever-settings {})
       (install-forever))))

(def settings-map {})

(def forever-unsupported
  [])

(def node-deb
  "https://raw.github.com/cinderella/deploy/master/debs/nodejs-0.6.10_amd64.deb")

(def node-script "
var http = require(\"http\");
http.createServer(function(request, response) {
    response.writeHead(200, {\"Content-Type\": \"text/plain\"});
    response.end(\"Hello World!\\n\");}).listen(8080);
console.log(\"Server running at http://localhost:8080/\");")

(deftest live-test
  (live-test/test-for
   [image (live-test/exclude-images (live-test/images) forever-unsupported)]
   (live-test/test-nodes
    [compute node-map node-types]
    {:forever
     {:image image
      :count 1
      :phases {:bootstrap (phase-fn (automated-admin-user))
               :settings (phase-fn
                           (nodejs-settings
                            {:deb
                             {:url node-deb
                              :md5 "597250b48364b4ed7ab929fb6a8410b8"}})
                           (forever-settings settings-map))
               :configure (phase-fn
                            (install-nodejs)
                            (install-forever)
                            (remote-file "node-script.js" :content node-script)
                            (forever-service
                             (stevedore/script "node-script.js")
                             :name "test" :action :start :max 1))
               :down (phase-fn
                       (forever-service
                        (stevedore/script "node-script.js")
                        :name "test" :action :stop))
               :verify-up (phase-fn
                            (wait-for-http-status
                             "http://localhost:8080/" 200 :url-name "node server")
                            (exec-checked-script
                             "check node-script is running"
                             (pipe
                              (wget "-O-" "http://localhost:8080/")
                              (grep -i (quoted "Hello World")))))
               :verify-down (phase-fn
                              (exec-checked-script
                               "check node-script is not running"
                               (when
                                   (pipe
                                    (wget "-O-" "http://localhost:8080/")
                                    (grep -i (quoted "Hello World")))
                                 (println "node-script still running" ">&2")
                                 (exit 1))))}}}
    (lift (:forever node-types)
          :phase [:settings :configure :verify-up :down :verify-down]
          :compute compute))))
