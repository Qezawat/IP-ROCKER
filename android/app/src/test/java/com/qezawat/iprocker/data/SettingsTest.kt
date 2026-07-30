package com.qezawat.iprocker.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Port selection is the one piece of settings logic with real behaviour rather
 * than plain storage, and it has to agree with internal/netports on the Go side:
 * never an empty set, and a single selection expressed as [ScanSettings.port]
 * with an empty [ScanSettings.ports] so config-link scans stay unchanged.
 */
class SettingsTest {

    @Test
    fun `default is the single port`() {
        val s = ScanSettings()
        assertEquals(listOf(443), s.selectedPorts())
        assertEquals("", s.ports)
    }

    @Test
    fun `adding a port records both and keeps canonical order`() {
        val s = ScanSettings().togglePort(8443).togglePort(2053)

        assertEquals(listOf(443, 2053, 8443), s.selectedPorts())
        assertEquals("443,2053,8443", s.ports)
        assertEquals(443, s.port)
    }

    @Test
    fun `removing back to one collapses to the single port form`() {
        val s = ScanSettings().togglePort(8443).togglePort(443)

        assertEquals(listOf(8443), s.selectedPorts())
        assertEquals("", s.ports)
        assertEquals(8443, s.port)
    }

    /** A scan with no port cannot run, so the last one is not removable. */
    @Test
    fun `the last port cannot be deselected`() {
        val s = ScanSettings().togglePort(443)

        assertEquals(listOf(443), s.selectedPorts())
        assertEquals("", s.ports)
    }

    @Test
    fun `a stored list wins over the single port`() {
        val s = ScanSettings(port = 443, ports = "2087, 2096")
        assertEquals(listOf(2087, 2096), s.selectedPorts())
    }

    @Test
    fun `garbage in a stored list is discarded`() {
        assertEquals(listOf(443), ScanSettings(ports = "abc,0,70000").selectedPorts())
        assertEquals(listOf(2053), ScanSettings(ports = "2053,2053,-1").selectedPorts())
    }
}
