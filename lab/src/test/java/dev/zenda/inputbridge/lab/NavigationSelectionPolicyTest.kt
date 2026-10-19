package dev.zenda.inputbridge.lab

import dev.zenda.inputbridge.lab.ui.isLabTabSelected
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSelectionPolicyTest {
    @Test
    fun selectedIndicatorFollowsCurrentDestinationWhenNavigationChanges() {
        var currentRoute: String? = "home"
        assertTrue(isLabTabSelected(currentRoute, "home"))
        assertFalse(isLabTabSelected(currentRoute, "shizuku"))

        currentRoute = "shizuku"
        assertTrue(isLabTabSelected(currentRoute, "shizuku"))
        assertFalse(isLabTabSelected(currentRoute, "home"))

        currentRoute = "direct_adb"
        assertTrue(isLabTabSelected(currentRoute, "direct_adb"))
        assertFalse(isLabTabSelected(currentRoute, "diagnostics"))
    }

    @Test
    fun noDestinationDoesNotMarkAnyTabSelected() {
        assertFalse(isLabTabSelected(null, "home"))
    }
}
