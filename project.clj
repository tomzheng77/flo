(defproject limiter "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/core.async "0.4.490"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [org.littleshoot/littleproxy "1.1.2"]
                 [org.apache.commons/commons-lang3 "3.8.1"]
                 [org.apache.commons/commons-exec "1.3"]
                 [commons-io/commons-io "2.6"]
                 [lock-key "1.5.0"]
                 [clj-time "0.15.0"]
                 [ring "1.7.0"]
                 [compojure "1.6.1"]
                 [http-kit "2.3.0"]
                 [cheshire "5.8.1"]
                 [java-time-literals "2018-04-06"]
                 [org.javassist/javassist "3.24.1-GA"]
                 [com.taoensso/nippy "2.14.0"]
                 [com.taoensso/encore "2.108.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.taoensso/sente "1.14.0-RC2"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [org.slf4j/log4j-over-slf4j "1.7.14"]
                 [org.slf4j/jul-to-slf4j "1.7.14"]
                 [org.slf4j/jcl-over-slf4j "1.7.14"]
                 [com.palletops/log-config "0.1.4"]
                 [criterium "0.4.4"]
                 [org.clojure/test.check "0.10.0-alpha3"]]
  :uberjar-name "limiter.jar"
  :main ^:skip-aot limiter.core
  :profiles {:uberjar {:aot :all}})
