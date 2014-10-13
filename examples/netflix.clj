(require '[imminent.core :as immi]
         '[imminent.executors :as executors])

;;
;; Supporting functions simulating external services
;;

(defn remote-service-a []
  (Thread/sleep 100)
  (immi/const-future "responseA"))

(defn remote-service-b []
  (Thread/sleep 40)
  (immi/const-future 100))

(defn remote-service-c [dep-from-a]
  (Thread/sleep 60)
  (immi/const-future (str "responseB_" dep-from-a)))


(defn remote-service-d [dep-from-b]
  (Thread/sleep 140)
  (immi/const-future (+ 40 dep-from-b)))

(defn remote-service-e [dep-from-b]
  (Thread/sleep 55)
  (immi/const-future (+ 5000 dep-from-b)))


;;
;; Examples 1, 2 & 3 are handled by the approach below
;; Original examples: https://gist.github.com/benjchristensen/4671081#file-futuresb-java-L13
;;


(defn example-1 []
  (let [f1     (remote-service-a)
        f2     (remote-service-b)
        f3     (immi/flatmap f1 remote-service-c)
        f4     (immi/flatmap f2 remote-service-d)
        f5     (immi/flatmap f2 remote-service-e)
        result (immi/sequence [f3 f4 f5])]
    (immi/on-success result
                     (fn [[r3 r4 r5]]
                       (prn (format "%s => %s" r3 (* r4 r5)))))))
(example-1)

(prefer-method print-method clojure.lang.IDeref java.util.Map)
(defn example-1 []
  (let [result (immi/mdo [f1  (remote-service-a)
                          f2  (remote-service-b)
                          f3  (remote-service-c f1)
                          f4  (remote-service-d f2)
                          f5  (remote-service-e f2)]
                         (immi/const-future [f3 f4 f5]))]
    (immi/on-success result
                     (fn [[r3 r4 r5]]
                       (prn (format "%s => %s" r3 (* r4 r5)))))))

(def x (example-1))

(def x )
(immi/pure immi/m-ctx [1 2 3])
;; #<Future@2d8af427: #<Success@7da343cc: [1 2 3]>>

(immi/const-future [1 2 3])
;; #<Future@4e10b06a: #<Success@29ae2730: [1 2 3]>>

;;
;; Examples 4 & 5 are handled by the approach below
;; Original examples: https://gist.github.com/benjchristensen/4671081#file-futuresb-java-L106
;;

(defn do-more-work [x]
  (prn "do more work => " x))

(defn example-4 []
  (let [futures (vector (remote-service-a)
                        (remote-service-b)
                        (remote-service-c "A")
                        (remote-service-c "B")
                        (remote-service-c "C")
                        (remote-service-d 1)
                        (remote-service-e 2)
                        (remote-service-e 3)
                        (remote-service-e 4)
                        (remote-service-e 5))]
    (doseq [f futures]
      (immi/on-success f do-more-work))))

(example-4)
