(defproject active "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :jvm-opts ["-Xmx2G"]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.zip "0.1.1"]
                 [clj-time "0.6.0"]
                 [storm "0.8.2"]
                 [gov.nasa/wwj "1.5.0-SNAPSHOT"]
                 ]
  :native-path "native/"
  :main active.core
  :plugins [[lein-deps-tree "0.1.2"]])
