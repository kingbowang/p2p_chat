package com.pengbo.p2pchat.chat

import io.libp2p.core.ChannelVisitor
import io.libp2p.core.Connection
import io.libp2p.core.ConnectionHandler
import io.libp2p.core.Libp2pException
import io.libp2p.core.P2PChannel
import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multiformats.MultiaddrDns
import io.libp2p.core.multiformats.Protocol
import io.libp2p.core.transport.Transport
import io.libp2p.etc.types.lazyVar
import io.libp2p.etc.types.toCompletableFuture
import io.libp2p.etc.types.toVoidCompletableFuture
import io.libp2p.transport.ConnectionUpgrader
import io.libp2p.transport.implementation.ConnectionBuilder
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

abstract class UdpNettyTransport(
    private val upgrader: ConnectionUpgrader
) : Transport {
    private var closed = false

    private val listeners = mutableMapOf<Multiaddr, Channel>()
    private val channels = mutableListOf<Channel>()

    private var workerGroup by lazyVar { NioEventLoopGroup() }

    override val activeListeners: Int
        get() = listeners.size
    override val activeConnections: Int
        get() = channels.size

    override fun listenAddresses(): List<Multiaddr> {
        return listeners.values.map {
            toMultiaddr(it.localAddress() as InetSocketAddress)
        }
    }

    override fun initialize() {
    }

    override fun close(): CompletableFuture<Unit> {
        closed = true

        val unbindsCompleted = listeners
            .map { (_, ch) -> ch }
            .map { it.close().toVoidCompletableFuture() }

        val channelsClosed = channels
            .toMutableList() // need a copy to avoid potential co-modification problems
            .map { it.close().toVoidCompletableFuture() }

        val everythingThatNeedsToClose = unbindsCompleted.union(channelsClosed)
        val allClosed = CompletableFuture.allOf(*everythingThatNeedsToClose.toTypedArray())

        return allClosed.thenApply {
            workerGroup.shutdownGracefully()
            Unit
        }
    } // close

    override fun listen(
        addr: Multiaddr,
        connHandler: ConnectionHandler,
        preHandler: ChannelVisitor<P2PChannel>?
    ): CompletableFuture<Unit> {
        if (closed) throw Libp2pException("Transport is closed")

        val connectionBuilder = makeConnectionBuilder(connHandler, false, preHandler = preHandler)
        val channelHandler = serverTransportBuilder(connectionBuilder, addr) ?: connectionBuilder

        val listener = Bootstrap().apply {
            group(workerGroup)
            channel(NioDatagramChannel::class.java)
            handler(channelHandler)
        }

        val bindComplete = listener.bind(fromMultiaddr(addr))

        bindComplete.also {
            synchronized(this@UdpNettyTransport) {
                listeners += addr to it.channel()
                it.channel().closeFuture().addListener {
                    synchronized(this@UdpNettyTransport) {
                        listeners -= addr
                    }
                }
            }
        }

        return bindComplete.toVoidCompletableFuture()
    } // listener

    protected abstract fun serverTransportBuilder(
        connectionBuilder: ConnectionBuilder,
        addr: Multiaddr
    ): ChannelHandler?

    override fun unlisten(addr: Multiaddr): CompletableFuture<Unit> {
        return listeners[addr]?.close()?.toVoidCompletableFuture()
            ?: throw Libp2pException("No listeners on address $addr")
    } // unlisten

    override fun dial(
        addr: Multiaddr,
        connHandler: ConnectionHandler,
        preHandler: ChannelVisitor<P2PChannel>?
    ): CompletableFuture<Connection> {
        if (closed) throw Libp2pException("Transport is closed")

        val remotePeerId = addr.getPeerId()
        val connectionBuilder = makeConnectionBuilder(connHandler, true, remotePeerId, preHandler)
        val channelHandler = clientTransportBuilder(connectionBuilder, addr) ?: connectionBuilder

        val chanFuture = Bootstrap().apply {
            group(workerGroup)
            channel(NioDatagramChannel::class.java)
            handler(channelHandler)
        }.connect(fromMultiaddr(addr))

        return chanFuture.toCompletableFuture()
            .thenCompose { connectionBuilder.connectionEstablished }
    } // dial

    protected abstract fun clientTransportBuilder(
        connectionBuilder: ConnectionBuilder,
        addr: Multiaddr
    ): ChannelHandler?

    private fun registerChannel(ch: Channel) {
        if (closed) {
            ch.close()
            return
        }

        synchronized(this@UdpNettyTransport) {
            channels += ch
            ch.closeFuture().addListener {
                synchronized(this@UdpNettyTransport) {
                    channels -= ch
                }
            }
        }
    } // registerChannel

    private fun makeConnectionBuilder(
        connHandler: ConnectionHandler,
        initiator: Boolean,
        remotePeerId: PeerId? = null,
        preHandler: ChannelVisitor<P2PChannel>?
    ) = ConnectionBuilder(
        this,
        upgrader,
        connHandler,
        initiator,
        remotePeerId,
        preHandler
    )

    protected fun handlesHost(addr: Multiaddr) =
        addr.hasAny(Protocol.IP4, Protocol.IP6, Protocol.DNS4, Protocol.DNS6, Protocol.DNSADDR)

    protected fun hostFromMultiaddr(addr: Multiaddr): String {
        val resolvedAddresses = MultiaddrDns.resolve(addr)
        if (resolvedAddresses.isEmpty()) {
            throw Libp2pException("Could not resolve $addr to an IP address")
        }

        return resolvedAddresses[0].components.find {
            it.protocol in arrayOf(Protocol.IP4, Protocol.IP6)
        }?.stringValue ?: throw Libp2pException("Missing IP4/IP6 in multiaddress $addr")
    }

    protected fun portFromMultiaddr(addr: Multiaddr) =
        addr.components.find { p -> p.protocol == Protocol.UDP }
            ?.stringValue?.toInt() ?: throw Libp2pException("Missing UDP in multiaddress $addr")

    private fun fromMultiaddr(addr: Multiaddr): InetSocketAddress {
        val host = hostFromMultiaddr(addr)
        val port = portFromMultiaddr(addr)
        return InetSocketAddress(host, port)
    } // fromMultiaddr

    abstract fun toMultiaddr(addr: InetSocketAddress): Multiaddr
}