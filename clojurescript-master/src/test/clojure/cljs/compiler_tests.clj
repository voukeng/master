;; Copyright (c) Rich Hickey. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns cljs.compiler-tests
  (:use clojure.test)
  (:require [cljs.analyzer :as ana]
            [cljs.compiler :as comp]
            [cljs.env :as env]
            [cljs.util :as util]
            [cljs.tagged-literals :as tags])
  (:import [java.io File]))

(def aenv (assoc-in (ana/empty-env) [:ns :name] 'cljs.user))
(def cenv (env/default-compiler-env))

#_(deftest should-recompile
  (let [src (File. "test/hello.cljs")
        dst (File/createTempFile "compilertest" ".cljs")
        opt {:optimize-constants true}
        optmod {:optimize-constants true :elide-asserts false}]
    (with-redefs [util/*clojurescript-version* {:major 0 :minor 0 :qualifier 42}]
      (env/with-compiler-env (env/default-compiler-env)
        (.setLastModified dst (- (.lastModified src) 100))
        (is (comp/requires-compilation? src dst opt))
        (comp/compile-file src dst opt)
        (is (not (comp/requires-compilation? src dst opt)))
        (is (comp/requires-compilation? src dst optmod))
        (comp/compile-file src dst optmod)
        (is (not (comp/requires-compilation? src dst optmod)))))))

(deftest fn-scope-munge
  (is (= (comp/munge
           (get-in
             (ana/analyze aenv
               '(defn foo []
                  (fn bar [])))
             [:init :name]))
         'cljs$user$foo))
  (is (= (comp/munge
           (get-in
             (ana/analyze aenv
               '(defn foo []
                  (fn bar [])))
             [:init :children 0 :children 0 :name]))
          'cljs$user$foo_$_bar))
  (is (= (comp/munge
           (get-in
             (ana/analyze aenv
               '(fn []
                  (fn console [])))
             [:children 0 :children 0 :name]))
         'cljs$user$console)))

(deftest test-js-negative-infinity
  (is (= (with-out-str
           (comp/emit
             (ana/analyze (assoc aenv :context :expr) 'js/-Infinity)))
          "-Infinity")))

(deftest test-munge-dotdot
  (is (= 'cljs.core._DOT__DOT_ (comp/munge 'cljs.core/..)))
  (is (= "cljs.core._DOT__DOT_" (comp/munge "cljs.core/..")))
  (is (= 'cljs.core._DOT__DOT_
         (ana/no-warn
           (env/with-compiler-env cenv
             (comp/munge
               (:info (ana/analyze {:ns {:name 'cljs.core}} 'cljs.core/..))))))))

(deftest test-resolve-dotdot
  (is (= '{:name cljs.core/..
           :ns   cljs.core}
         (ana/no-warn
           (env/with-compiler-env cenv
             (ana/resolve-var {:ns {:name 'cljs.core}} '..))))))

(deftest test-cljs-428
  (letfn [(check-docs [docs]
            (is (= 1 (count (re-seq #"\*/" docs)))))]
    (check-docs (with-out-str
                  (env/ensure
                    (comp/emit-comment "/* multiline comments */" nil))))
    (check-docs (with-out-str
                  (comp/emit
                    (ana/analyze aenv
                      '(defn foo "foo is */ like this /*/" [] (+ 1 1))))))))

(comment
  (env/with-compiler-env cenv
    (comp/emit
      (ana/analyze aenv
        '(defn foo ([a]) ([a b])))))
  )

(defn capture-warnings* [f]
  (let [capture (atom [])
        tracker (fn [warning-type env & [extra]]
                  (when (warning-type ana/*cljs-warnings*)
                    (let [err (ana/error-message warning-type extra)
                          msg (ana/message env (str "WARNING: " err))]
                      (swap! capture conj [warning-type msg]))))]
    (ana/with-warning-handlers [tracker]
      (f))
    @capture))

(defmacro capture-warnings [& body]
  `(capture-warnings* (fn [] ~@body)))

(deftest or-doesnt-create-bindings
  (let [cenv (atom @cenv)]
    (binding [ana/*cljs-static-fns* true
              ana/*analyze-deps* false]
      (env/with-compiler-env cenv
        (ana/analyze-file (File. "src/main/cljs/cljs/core.cljs"))
        (let [warnings (-> (capture-warnings
                             (with-out-str
                               (comp/emit
                                 (ana/analyze aenv
                                   '(let [{:keys [a] :or {b 2}} {:a 1}] [a b]))))))]
          (is (= (ffirst warnings) :undeclared-var))
          (is (.startsWith (-> warnings first second)
                "WARNING: Use of undeclared Var cljs.user/b")))))))

(deftest no-warn-on-emit-invoke-protocol-method
  (let [define-foo #(assoc-in % [::ana/namespaces 'cljs.user :defs 'foo]
                              {:ns 'cljs.user
                               :name 'cljs.user/foo
                               :fn-var true
                               :method-params '([x])
                               :protocol 'cljs.user/Foo})
        aenv-with-foo (define-foo aenv)
        cenv-with-foo (define-foo @cenv)]
    (binding [ana/*cljs-static-fns* true]
      (are [form]
        (empty?
         (capture-warnings
          (env/with-compiler-env (atom cenv-with-foo)
            (with-out-str
              (comp/emit
               (ana/analyze aenv-with-foo form))))))

        '(cljs.user/foo nil)
        '(cljs.user/foo 0)
        '(cljs.user/foo (inc 0))
        '(cljs.user/foo "")
        '(cljs.user/foo true)
        '(cljs.user/foo false)
        '(cljs.user/foo (nil? nil))
        '(cljs.user/foo (fn [x] x))
        `(cljs.user/foo ~(tags/->JSValue {}))
        `(cljs.user/foo ~(tags/->JSValue []))
        '(cljs.user/foo (make-array 0))))))

(deftest test-cljs-1643
  (is (thrown-with-msg? Exception #"is not a valid ClojureScript constant."
        (comp/emit-constant clojure.core/inc))))

(def test-cljs-1925-code
  '(do
     (defprotocol X
       (x [x]))

     (defprotocol Y
       (y [y]))

     (extend-protocol X
       js/RegExp
       (x [x]
         (y x)))

     (extend-protocol Y
       js/RegExp
       (y [y]
         :y))))

(def specify-test-code
  '(do
     (defprotocol IBug
       (bug [this other] "A sample protocol"))

     (defn MyBug [])
     (specify! (.-prototype MyBug)
       IBug
       (bug [this other]
         "bug")
       Object
       (foo [this]
         (bug this 3)))))

(deftest test-cljs-1925
  (let [opts {:static-fns true}
        cenv (env/default-compiler-env opts)]
    (is (= [] (binding [ana/*unchecked-if* false
                        ana/*cljs-static-fns* true]
                (capture-warnings
                  (env/with-compiler-env cenv
                    (with-out-str
                      (comp/emit
                        (comp/with-core-cljs
                          opts
                          (fn [] (ana/analyze aenv test-cljs-1925-code nil opts)))))))))))
  (let [opts {:static-fns true}
        cenv (env/default-compiler-env opts)]
    (is (= [] (binding [ana/*unchecked-if* false
                        ana/*cljs-static-fns* true]
                (capture-warnings
                  (env/with-compiler-env cenv
                    (with-out-str
                      (comp/emit
                        (comp/with-core-cljs
                          opts
                          (fn [] (ana/analyze aenv specify-test-code nil opts))))))))))))

;; CLJS-1225

(comment
  (binding [ana/*cljs-static-fns* true]
    (env/with-compiler-env cenv
      (comp/emit
        (ana/analyze aenv
          '(defn incme []
             (let [incme (fn [a queue & args])]
               (println (incme 1 [1] 1 1))))))))
  )

(comment
  ;; combining boolean hint w/ static fns

  (binding [ana/*cljs-static-fns* true]
    (env/with-compiler-env cenv
      (comp/emit
        (ana/analyze aenv
          '(defn foo [x]
             (if ^boolean (goog.array/isEmpty x)
               true
               false))))))
  )
