package com.example.foodiary.data.remote.off

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

object OffNetworkDiagnostics {

    fun snapshot(context: Context?, host: String): String {
        if (context == null) return "context=null host=$host"

        return buildString {
            appendLine("host=$host")
            appendLine("dns=skipped_by_debug_logger")
            append(networkSummary(context))
        }.trimEnd()
    }

    private fun networkSummary(context: Context): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "connectivityManager=null"

        val activeNetwork = manager.activeNetwork
        val activeCapabilities = activeNetwork?.let(manager::getNetworkCapabilities)

        return buildString {
            appendLine("activeNetwork=${networkName(activeNetwork)}")
            appendLine("activeCapabilities=${capabilitiesSummary(activeCapabilities)}")
            appendLine("allNetworks=")
            manager.allNetworks.forEach { network ->
                append("  ")
                append(networkName(network))
                append(" -> ")
                appendLine(capabilitiesSummary(manager.getNetworkCapabilities(network)))
            }
        }.trimEnd()
    }

    private fun networkName(network: Network?): String {
        return network?.toString() ?: "null"
    }

    private fun capabilitiesSummary(capabilities: NetworkCapabilities?): String {
        if (capabilities == null) return "null"

        val transports = buildList {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("WIFI")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("CELLULAR")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ETHERNET")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("VPN")
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) add("BLUETOOTH")
        }.ifEmpty { listOf("none") }

        val capabilitiesList = buildList {
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) add("INTERNET")
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) add("VALIDATED")
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) add("NOT_VPN")
            if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)) add("NOT_RESTRICTED")
        }.ifEmpty { listOf("none") }

        return "transports=${transports.joinToString("+")} capabilities=${capabilitiesList.joinToString("+")}"
    }
}
