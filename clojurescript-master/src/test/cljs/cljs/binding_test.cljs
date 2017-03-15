;; Copyright (c) Rich Hickey. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns cljs.binding-test
  (:require [cljs.test :refer-macros [deftest is]]
            [cljs.binding-test-other-ns :as o]))

(deftest test-binding
  (is (binding [o/*foo* 2]
        (= o/*foo* 2)))
  (is (= o/*foo* 1)))

(deftest test-with-redefs
  (is (with-redefs [o/bar 2]
        (= o/bar 2)))
  (is (= o/bar 10)))
