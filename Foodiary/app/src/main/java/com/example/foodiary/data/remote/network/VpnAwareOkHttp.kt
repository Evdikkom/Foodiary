package com.example.foodiary.data.remote.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

object VpnAwareOkHttp {

    fun applyTo(
        builder: OkHttpClient.Builder,
        ipv4Only: Boolean = false,
        bypassVpnWhenActive: Boolean = false
    ): OkHttpClient.Builder {
        return if (ipv4Only || bypassVpnWhenActive) {
            val configuredBuilder = if (bypassVpnWhenActive) {
                builder.socketFactory(DynamicNetworkSocketFactory)
            } else {
                builder
            }

            configuredBuilder.dns(
                DynamicNetworkDns(
                    ipv4Only = ipv4Only,
                    bypassVpnWhenActive = bypassVpnWhenActive
                )
            )
        } else {
            builder
        }
    }

    private object DynamicNetworkSocketFactory : SocketFactory() {
        override fun createSocket(): Socket = currentSocketFactory().createSocket()

        override fun createSocket(host: String, port: Int): Socket =
            currentSocketFactory().createSocket(host, port)

        override fun createSocket(
            host: String,
            port: Int,
            localHost: InetAddress,
            localPort: Int
        ): Socket = currentSocketFactory().createSocket(host, port, localHost, localPort)

        override fun createSocket(host: InetAddress, port: Int): Socket =
            currentSocketFactory().createSocket(host, port)

        override fun createSocket(
            address: InetAddress,
            port: Int,
            localAddress: InetAddress,
            localPort: Int
        ): Socket = currentSocketFactory().createSocket(address, port, localAddress, localPort)
    }

    private class DynamicNetworkDns(
        private val ipv4Only: Boolean,
        private val bypassVpnWhenActive: Boolean
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = if (bypassVpnWhenActive) {
                preferredNonVpnNetwork()
                    ?.getAllByName(hostname)
                    ?.toList()
                    ?: Dns.SYSTEM.lookup(hostname)
            } else {
                Dns.SYSTEM.lookup(hostname)
            }

            return if (ipv4Only) {
                addresses.filterIsInstance<Inet4Address>().ifEmpty { addresses }
            } else {
                addresses
            }
        }
    }

    private fun currentSocketFactory(): SocketFactory {
        return preferredNonVpnNetwork()?.socketFactory ?: SocketFactory.getDefault()
    }

    private fun preferredNonVpnNetwork(): Network? {
        val manager = connectivityManager() ?: return null
        val activeNetwork = manager.activeNetwork ?: return null
        val activeCapabilities = manager.getNetworkCapabilities(activeNetwork) ?: return null
        if (!activeCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return null

        return manager.allNetworks.firstOrNull { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@firstOrNull false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
        }
    }

    private fun connectivityManager(): ConnectivityManager? {
        val context = FoodiaryNetworkContext.context() ?: return null
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
}
