(ns flo.server
  (:import (com.corundumstudio.socketio Configuration SocketIOServer)
           (com.corundumstudio.socketio.listener DataListener)))

(defn new-listener [server]
  (proxy [DataListener] []
    (onData [client data ack-request]
      (println data))))

(defn launch []
  (let [config (new Configuration)]
    (.setHostname config "localhost")
    (.setPort config 9092)
    (let [server (new SocketIOServer config)]
      (.addEventListener server "hello" String (new-listener server))
      (.start server)
      (Thread/sleep Integer/MAX_VALUE)
      (.stop server))))

(defn -main [& args]
  (launch))
