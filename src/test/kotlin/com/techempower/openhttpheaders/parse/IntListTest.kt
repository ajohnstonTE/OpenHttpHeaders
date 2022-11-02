package com.techempower.openhttpheaders.parse

import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IntListTest : FunSpec({
  context("plusAssign/get") {
    test("single value") {
      val ints = IntList()
      ints += 6
      ints[0] shouldBe 6
    }
    test("two values") {
      val ints = IntList()
      ints += 6
      ints += 11
      ints[0] shouldBe 6
      ints[1] shouldBe 11
    }
    test("10000 values") {
      val ints = IntList()
      for(i in 0 until 10000) {
        ints += i * 3
      }
      for(i in 0 until 10000) {
        ints[i] shouldBe i * 3
      }
    }
  }
  context("size") {
    test("single value") {
      val ints = IntList()
      ints += 6
      ints.size shouldBe 1
    }
    test("two values") {
      val ints = IntList()
      ints += 6
      ints += 11
      ints.size shouldBe 2
    }
    test("10000 values") {
      val ints = IntList()
      for(i in 0 until 10000) {
        ints += i * 3
      }
      ints.size shouldBe 10000
    }
  }
  context("truncate") {
    test("single value") {
      val ints = IntList()
      ints += 6
      // Sanity check
      ints.size shouldBe 1
      ints.truncate(0)
      ints.size shouldBe 0
    }
    test("two values") {
      val ints = IntList()
      ints += 6
      ints += 11
      // Sanity check
      ints.size shouldBe 2
      ints.truncate(1)
      ints.size shouldBe 1
      ints[0] shouldBe 6
    }
    test("10000 values") {
      val ints = IntList()
      for(i in 0 until 10000) {
        ints += i * 3
      }
      // Sanity check
      ints.size shouldBe 10000
      ints.truncate(499)
      ints.size shouldBe 499
      for(i in 0 until 499) {
        ints[i] shouldBe i * 3
      }
    }
  }
  context("set") {
    test("index 0") {
      val ints = IntList()
      ints += 5
      // Sanity check
      ints[0] shouldBe 5
      ints[0] = 1
      ints[0] shouldBe 1
    }
    test("index 0 and 1") {
      val ints = IntList()
      ints += 0
      ints += 0
      // Sanity check
      ints[0] shouldBe 0
      ints[1] shouldBe 0
      ints[0] = 3
      ints[1] = 5
      ints[0] shouldBe 3
      ints[1] shouldBe 5
    }
    test("index 0 to 9999") {
      val ints = IntList()
      for(i in 0 until 10000) {
        ints += i * 3
      }
      for(i in 0 until 10000) {
        ints[i] = i * 4
      }
      for(i in 0 until 10000) {
        ints[i] shouldBe i * 4
      }
    }
    context("unpopulated index should throw error") {
      test("no additions, index 0") {
        val ints = IntList()
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[0] = 2
        }
      }
      test("one addition, index 1") {
        val ints = IntList()
        ints += 1
        // Sanity check
        ints[0] = 2
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[1] = 2
        }
      }
      test("1000 additions, index 1000") {
        val ints = IntList()
        for (i in 0 until 1000) {
          ints += i
        }
        // Sanity check
        ints[999] = 2
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[1000] = 2
        }
      }
      test("1000 additions, truncated to 500, index 500") {
        val ints = IntList()
        for (i in 0 until 1000) {
          ints += i
        }
        ints.truncate(500)
        // Sanity check
        ints[499] = 2
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[500] = 2
        }
      }
      test("2 additions, truncated to 1, index 1") {
        val ints = IntList()
        ints += 0
        ints += 0
        ints.truncate(1)
        // Sanity check
        ints[0] = 2
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[1] = 2
        }
      }
      test("1 additions, truncated to 0, index 0") {
        val ints = IntList()
        ints += 0
        ints.truncate(0)
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[0] = 2
        }
      }
    }
  }
  context("get") {
    context("unpopulated index should throw error") {
      test("no additions, index 0") {
        val ints = IntList()
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[0] = 2
        }
      }
      test("one addition, index 1") {
        val ints = IntList()
        ints += 1
        // Sanity check
        ints[0]
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[1]
        }
      }
      test("1000 additions, index 1000") {
        val ints = IntList()
        for (i in 0 until 1000) {
          ints += i
        }
        // Sanity check
        ints[999]
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[1000]
        }
      }
      test("1000 additions, truncated to 500, index 500") {
        val ints = IntList()
        for (i in 0 until 1000) {
          ints += i
        }
        ints.truncate(500)
        // Sanity check
        ints[499]
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[500]
        }
      }
      test("2 additions, truncated to 1, index 1") {
        val ints = IntList()
        ints += 0
        ints += 0
        ints.truncate(1)
        // Sanity check
        ints[0]
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[1]
        }
      }
      test("1 additions, truncated to 0, index 0") {
        val ints = IntList()
        ints += 0
        ints.truncate(0)
        shouldThrowUnit<IndexOutOfBoundsException> {
          ints[0]
        }
      }
    }
  }
})
