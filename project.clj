(defproject fakeaws "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :repositories {"findify" "https://dl.bintray.com/findify/maven/"}
  :dependencies [[amazonica "0.3.152"]
                 [org.clojure/clojure "1.10.0"]
                 [org.gaul/s3proxy "1.7.0"]
                 [io.findify/sqsmock_2.11 "0.3.2"]
                 [org.clojure/tools.cli "1.0.194"]]
  :main ^:skip-aot fakeaws.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
