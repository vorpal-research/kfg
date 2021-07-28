package org.jetbrains.research.kfg

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageUnitTest {
    val prefix = "org.jetbrains.kfg."
    @Test
    fun testSelf() {
        assertFalse { Package.parse(prefix + "ir").isParent(Package.parse(prefix + "ir")) }
    }

    @Test
    fun testParent() {
        assertTrue { Package.parse(prefix + "ir").isParent(Package.parse(prefix + "ir.value")) }
    }

    @Test
    fun testAncestor() {
        assertFalse { Package.parse(prefix + "ir").isParent(Package.parse(prefix + "ir.value.instruction")) }
    }
}
