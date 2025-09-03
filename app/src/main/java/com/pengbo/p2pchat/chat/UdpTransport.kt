package com.pengbo.p2pchat.chat

import io.libp2p.core.InternalErrorException
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multiformats.Protocol
import io.libp2p.core.multiformats.Protocol.IP4
import io.libp2p.core.multiformats.Protocol.IP6
import io.libp2p.core.multiformats.Protocol.UDP
import io.libp2p.transport.ConnectionUpgrader
import io.libp2p.transport.implementation.ConnectionBuilder
import io.netty.channel.ChannelHandler
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress

open class UdpTransport(upgrader: ConnectionUpgrader) : UdpNettyTransport(upgrader) {
    override fun clientTransportBuilder(
        connectionBuilder: ConnectionBuilder,
        addr: Multiaddr
    ): ChannelHandler? = null

    override fun handles(addr: Multiaddr): Boolean =
        handlesHost(addr) && addr.has(UDP) && !addr.has(Protocol.DNSADDR)

    override fun serverTransportBuilder(
        connectionBuilder: ConnectionBuilder,
        addr: Multiaddr
    ): ChannelHandler? = null

    override fun toMultiaddr(addr: InetSocketAddress): Multiaddr {
        val proto = when (addr.address) {
            is Inet4Address -> IP4
            is Inet6Address -> IP6
            else -> throw InternalErrorException("Unknown address type $addr")
        }
        return Multiaddr.empty()
            .withComponent(proto, addr.address.hostAddress)
            .withComponent(UDP, addr.port.toString())
    }
}