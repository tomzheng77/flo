(defproject octavia "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.littleshoot/littleproxy "1.1.2"]
                 [com.taoensso/timbre "4.10.0"]
                 [org.apache.commons/commons-lang3 "3.8.1"]
                 [org.apache.commons/commons-exec "1.3"]
                 [net.lightbody.bmp/mitm "2.1.5"]
                 [org.clojure/test.check "0.10.0-alpha3"]
                 [commons-io/commons-io "2.6"]
                 [lock-key "1.5.0"]
                 [http-kit "2.3.0"]
                 [clj-time "0.15.0"]
                 [compojure "1.6.1"]
                 [org.slf4j/log4j-over-slf4j "1.7.14"]
                 [org.slf4j/jul-to-slf4j "1.7.14"]
                 [org.slf4j/jcl-over-slf4j "1.7.14"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [com.palletops/log-config "0.1.4"]
                 [cheshire "5.8.1"]
                 [java-time-literals "2018-04-06"]
                 [criterium "0.4.4"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [org.javassist/javassist "3.24.1-GA"]]
  :main ^:skip-aot octavia.core
  :profiles {:uberjar {:aot :all}})
