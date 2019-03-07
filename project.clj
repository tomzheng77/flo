(defproject sayaka "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.littleshoot/littleproxy "1.1.2"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.apache.commons/commons-lang3 "3.8.1"]
                 [org.apache.commons/commons-exec "1.3"]
                 [net.lightbody.bmp/mitm "2.1.5"]
                 [org.clojure/test.check "0.10.0-alpha3"]
                 [commons-io/commons-io "2.6"]
                 [http-kit "2.3.0"]
                 [clj-time "0.15.0"]
                 [compojure "1.6.1"]]
  :main ^:skip-aot monika-clj.core
  :profiles {:uberjar {:aot :all}})
