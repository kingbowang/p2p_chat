package com.pengbo.p2pchat.chat

import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import io.libp2p.core.PeerId
import io.libp2p.core.PeerInfo
import io.libp2p.core.Stream
import io.libp2p.core.dsl.host
import io.libp2p.core.multiformats.Multiaddr
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.CompletableFuture.runAsync

typealias OnMessage = (String) -> Unit

class ChatNode(private val printMsg: OnMessage) {
    companion object {
        private const val TAG = "ChatNode"
        private fun privateNetworkAddress(): InetAddress {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            val addresses = interfaces.flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .sortedBy { it.hostAddress }
            return if (addresses.isNotEmpty()) {
                addresses[0]
            } else {
                InetAddress.getLoopbackAddress()
            }
        }
    }

    private data class Friend(
        var name: String,
        val controller: ChatController
    )

    private val knownNodes = mutableSetOf<PeerId>()
    private val peers = mutableMapOf<PeerId, Friend>()
    private val privateAddress: InetAddress = privateNetworkAddress()
    private val port = 4009
    private val phoneNumber = "15008475882"
    private val chatHost = host {
        identity {
            factory = { EncryptUtils.generatePrivateKeyFromNumber(phoneNumber) }
        }
        protocols {
            +Chat(::messageReceived)
        }
        network {
            listen("/ip4/$address/tcp/$port")
        }
    }

    val peerId = chatHost.peerId
    val address: String
        get() {
            return privateAddress.hostAddress ?: ""
        }

    // init
    init {
        LogUtils.dTag(TAG, "init")
        chatHost.start().get()
        chatHost.listenAddresses().forEach { printMsg("> $it") }
    }

    // send
    fun send(message: String) {
        LogUtils.dTag(TAG, "send")
        val messageModel = MessageModel(action = "text_msg", content = message)
        peers.values.forEach { it.controller.send(GsonUtils.toJson(messageModel)) }
    }

    // stop
    fun stop() {
        LogUtils.dTag(TAG, "stop")
        chatHost.stop()
    }

    // messageReceived
    private fun messageReceived(id: PeerId, msg: String) {
        LogUtils.dTag(TAG, "messageReceived, peerId:$id, msg:$msg")
        val messageModel = GsonUtils.fromJson(msg, MessageModel::class.java)
        when (messageModel.action) {
            "/who" -> {
                addPeer(messageModel.ip, messageModel.port, id)
            }

            "text_msg" -> {
                printMsg("${id.toBase58()} > ${messageModel.content}")
            }

            else -> {
                LogUtils.dTag(TAG, "unknown message")
                return
            }
        }
    }

    // peerFound
    private fun peerFound(info: PeerInfo) {
        LogUtils.dTag(TAG, "peerFound, info:$info")
        if (info.peerId == chatHost.peerId || knownNodes.contains(info.peerId)) {
            return
        }
        knownNodes.add(info.peerId)
        val chatConnection = connectChat(info) ?: return
        chatConnection.first.closeFuture().thenAccept {
            printMsg("${peers[info.peerId]?.name} disconnected.")
            peers.remove(info.peerId)
            knownNodes.remove(info.peerId)
        }
        printMsg("Connected to new peer ${info.peerId}")
        val messageModel = MessageModel(action = "/who", ip = address, port = port)
        chatConnection.second.send(GsonUtils.toJson(messageModel))
        peers[info.peerId] = Friend(
            info.peerId.toBase58(),
            chatConnection.second
        )
    }

    // connectChat
    private fun connectChat(info: PeerInfo): Pair<Stream, ChatController>? {
        LogUtils.dTag(TAG, "connectChat, info:$info")
        try {
            val chat = Chat(::messageReceived).dial(chatHost, info.peerId, info.addresses[0])
            return Pair(chat.stream.get(), chat.controller.get())
        } catch (e: Exception) {
            LogUtils.eTag(TAG, e.message)
            e.message?.let { printMsg(it) }
            return null
        }
    }

    fun addPeerByNumber(ip: String, port: Int, phoneNumber: String) {
        val peerId = EncryptUtils.getPeerIdFromPhone(phoneNumber)
        addPeer(ip, port, peerId)
    }

    private fun addPeer(ip: String, port: Int, peerId: PeerId) {
        LogUtils.dTag(TAG, "addPeer, ip:$ip, port:$port, peerId:$peerId")
        runAsync {
            try {
                val address = Multiaddr("/ip4/$ip/tcp/$port/p2p/$peerId")
                val peerInfo = PeerInfo(peerId, listOf(address))
                peerFound(peerInfo)
            } catch (e: Exception) {
                LogUtils.eTag(TAG, e.message)
                e.message?.let { printMsg(it) }
                e.printStackTrace()
            }
        }
    }
}
