;; Copyright (c) Rich Hickey. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns foo.ns-shadow-test
  (:require [cljs.test :refer-macros [deftest is]]
            baz))

(defn bar [] 1)

(defn quux [foo]
  (+ (foo.ns-shadow-test/bar) foo))

(defn id [x] x)

(defn foo [] (id 42))

(defn baz
  ([] (baz 2))
  ([x] (quux 2)))

(deftest test-shadow
  (is (= (quux 2) 3))
  (is (= (foo) 42))
  (is (= (baz) 3)))
