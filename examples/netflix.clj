(require '[imminent.core :as immi]
         '[imminent.executors :as executors])

;;
;; Supporting functions simulating external services
;;

(defn remote-service-a []
  (Thread/sleep 100)
  "responseA")

(defn remote-service-b []
  (Thread/sleep 40)
  100)

(defn remote-service-c [dep-from-a]
  (Thread/sleep 60)
  (str "responseB_" dep-from-a))


(defn remote-service-d [dep-from-b]
  (Thread/sleep 140)
  (+ 40 dep-from-b))

(defn remote-service-e [dep-from-b]
  (Thread/sleep 55)
  (+ 5000 dep-from-b))


;;
;; Examples 1, 2 & 3 are handled by the approach below
;; Original examples: https://gist.github.com/benjchristensen/4671081#file-futuresb-java-L13
;;


(defn example-1 []
  (let [f1     (immi/future (remote-service-a))
        f2     (immi/future (remote-service-b))
        f3     (-> f1 (immi/map #(remote-service-c %)))
        f4     (-> f2 (immi/map #(remote-service-d %)))
        f5     (-> f2 (immi/map #(remote-service-e %)))
        result (immi/sequence [f3 f4 f5])]
    (immi/on-success result
                     (fn [[r3 r4 r5]]
                       (prn (format "%s => %s" r3 (* r4 r5)))))))



;;
;; Examples 4 & 5 are handled by the approach below
;; Original examples: https://gist.github.com/benjchristensen/4671081#file-futuresb-java-L106
;;

(defn do-more-work [x]
  (prn "do more work => " x))

(defn example-4 []
  (let [futures (conj []
                      (immi/future (remote-service-a))
                      (immi/future (remote-service-b))
                      (immi/future (remote-service-c "A"))
                      (immi/future (remote-service-c "B"))
                      (immi/future (remote-service-c "C"))
                      (immi/future (remote-service-d 1))
                      (immi/future (remote-service-e 2))
                      (immi/future (remote-service-e 3))
                      (immi/future (remote-service-e 4))
                      (immi/future (remote-service-e 5)))]
    (doseq [f futures]
      (immi/on-success f do-more-work))))
