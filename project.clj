(defproject flo "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  
  
  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.597"]
                 [org.clojure/core.async "1.0.567"]
                 [cljsjs/react "16.8.3-0"]
                 [cljsjs/react-dom "16.8.3-0"]
                 [cljsjs/moment "2.24.0-0"]
                 [reagent "0.10.0"]
                 [re-frame "0.12.0"]
                 [clj-commons/cljss "1.6.4"]
                 [sablono "0.8.5"]
                 [joda-time/joda-time "2.10.5"]
                 [ring "1.8.0"]
                 [ring/ring-defaults "0.3.2"]
                 [http-kit "2.3.0"]
                 [compojure "1.6.1"]
                 [cljs-http "0.1.46"]
                 [org.clojure/core.match "0.3.0-alpha5"]
                 [hiccup "1.0.5"]
                 [garden "1.3.6"]
                 [com.taoensso/nippy "2.14.0"]
                 [com.taoensso/encore "2.108.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [com.taoensso/sente "1.14.0-RC2"]
                 [com.fzakaria/slf4j-timbre "0.3.12"]
                 [org.slf4j/log4j-over-slf4j "1.7.14"]
                 [org.slf4j/jul-to-slf4j "1.7.14"]
                 [org.slf4j/jcl-over-slf4j "1.7.14"]
                 [com.google.guava/guava "27.1-jre"]
                 [org.clojure/data.avl "0.0.18"]
                 [commons-codec/commons-codec "1.12"]
                 [prismatic/dommy "1.1.0"]
                 [hipo "0.5.2"]
                 [testdouble/clojurescript.csv "0.4.5"]

                 ; requires /flo/datomic/bin/maven-install
                 [com.datomic/datomic-pro "0.9.5786"]]

  :plugins [[lein-figwheel "0.5.19"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :uberjar-name "flo.jar"
  :main ^:skip-aot flo.server.server

  ; https://github.com/technomancy/leiningen/issues/2166
  :auto-clean false

  :source-paths ["src"]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]

                ;; The presence of a :figwheel configuration here
                ;; will cause figwheel to inject the figwheel client
                ;; into your build
                :figwheel true

                :compiler {:main flo.client.client
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/flo.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true
                           ;; To console.log CLJS data-structures make sure you enable devtools in Chrome
                           ;; https://github.com/binaryage/cljs-devtools
                           :preloads [devtools.preload]
                           :npm-deps {:diff "4.0.1"}
                           :install-deps true}}
               ;; This next build is a compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/compiled/flo.js"
                           :main flo.client.client
                           :optimizations :simple
                           :pretty-print false
                           :npm-deps {:diff "4.0.1"}
                           :externs ["externs.js"]
                           :install-deps true}}]}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this

             ;; doesn't work for you just run your own server :) (see lein-ring)

             :ring-handler flo.server.server/dev-app

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you are using emacsclient you can just use
             ;; :open-file-command "emacsclient"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;: server-logfile "tmp/logs/figwheel-logfile.log"

             ;; to pipe all the output to the repl
             :server-logfile false
             }

	:repl-options {
	             ;; If nREPL takes too long to load it may timeout,
	             ;; increase this to wait longer before timing out.
	             ;; Defaults to 30000 (30 seconds)
	             :timeout 120000 }

  ;; Setting up nREPL for Figwheel and ClojureScript dev
  ;; Please see:
  ;; https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[binaryage/devtools "1.0.0"]
                                  [figwheel-sidecar "0.5.19"]
                                  [cider/piggieback "0.4.2"]]
                   ;; need to add dev source path here to get user.clj loaded
                   :source-paths ["src" "dev"]
                   ;; for CIDER
                   ;; :plugins [[cider/cider-nrepl "0.12.0"]]
                   :repl-options {:nrepl-middleware [cider.piggieback/wrap-cljs-repl]}
                   ;; need to add the compliled assets to the :clean-targets
                   :clean-targets ^{:protect false} ["resources/public/js/compiled" :target-path]}})
