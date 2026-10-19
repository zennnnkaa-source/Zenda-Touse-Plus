package dev.zenda.inputbridge.lab.ui

/** The bottom-navigation indicator always follows the actual NavController destination route. */
internal fun isLabTabSelected(currentDestinationRoute: String?, tabRoute: String): Boolean =
    currentDestinationRoute == tabRoute
