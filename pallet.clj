;;; Pallet project configuration file

(require
 '[pallet.crate.forever-test :refer [test-spec]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject forever-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [(group-spec "forever-live-test"
             :extends [with-automated-admin-user
                       test-spec]
             :roles #{:live-test :default})])
