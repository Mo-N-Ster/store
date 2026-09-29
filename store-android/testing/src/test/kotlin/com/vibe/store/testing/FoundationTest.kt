package com.vibe.store.testing
import com.vibe.store.application.FoundationEnvironment
import com.vibe.store.application.FoundationServiceImpl
import com.vibe.store.domain.FoundationStage
import org.junit.Assert.*
import org.junit.Test

class FoundationTest {
    @Test fun foundationUsesInjectedPort() {
        var calls = 0
        val service = FoundationServiceImpl(FoundationEnvironment { calls++; "isolated adapter" })
        val result = service.inspect()
        assertEquals(FoundationStage.NATIVE_FOUNDATION.name, result.milestone)
        assertEquals("isolated adapter", result.storageAdapter)
        assertEquals(1, calls)
    }
    @Test fun infrastructureErrorsAreNotSwallowed() {
        val failure = IllegalStateException("synthetic failure")
        val service = FoundationServiceImpl(FoundationEnvironment { throw failure })
        try { service.inspect(); fail("Expected explicit failure") } catch (e: IllegalStateException) { assertSame(failure, e) }
    }
}
