(defproject com.palletops/forever-crate "0.8.0-alpha.2"
  :description "Crate for Forever installation"
  :url "http://github.com/pallet/forever-crate"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.palletops/pallet "0.8.0-RC.3"]]
  :resource {:resource-paths ["doc-src"]
             :target-path "target/classes/pallet_crate/forever_crate/"
             :includes [#"doc-src/USAGE.*"]}
  :prep-tasks ["resource" "crate-doc"])
