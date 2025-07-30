package com.pengbo.p2pchat.chat

import com.blankj.utilcode.util.LogUtils
import io.libp2p.core.PeerId
import io.libp2p.core.Stream
import io.libp2p.core.multistream.ProtocolId
import io.libp2p.core.multistream.StrictProtocolBinding
import io.libp2p.etc.types.toByteBuf
import io.libp2p.protocol.ProtocolHandler
import io.libp2p.protocol.ProtocolMessageHandler
import io.netty.buffer.ByteBuf
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture

interface ChatController {
    fun send(message: String)
}

typealias OnChatMessage = (PeerId, String) -> Unit

class Chat(chatCallback: OnChatMessage) : ChatBinding(ChatProtocol(chatCallback))

const val PROTOCOL_ID: ProtocolId = "/pengbo/p2p_chat/0.1.0"

open class ChatBinding(echo: ChatProtocol) :
    StrictProtocolBinding<ChatController>(PROTOCOL_ID, echo)

open class ChatProtocol(
    private val chatCallback: OnChatMessage
) : ProtocolHandler<ChatController>(Long.MAX_VALUE, Long.MAX_VALUE) {

    companion object {
        private const val TAG = "ChatProtocol"
    }

    override fun onStartInitiator(stream: Stream) = onStart(stream)
    override fun onStartResponder(stream: Stream) = onStart(stream)

    private fun onStart(stream: Stream): CompletableFuture<ChatController> {
        val ready = CompletableFuture<Void>()
        val handler = Chatter(chatCallback, ready)
        stream.pushHandler(handler)
        return ready.thenApply { handler }
    }

    open inner class Chatter(
        private val chatCallback: OnChatMessage,
        val ready: CompletableFuture<Void>
    ) : ProtocolMessageHandler<ByteBuf>, ChatController {
        lateinit var stream: Stream

        override fun onActivated(stream: Stream) {
            LogUtils.dTag(TAG, "onActivated")
            this.stream = stream
            ready.complete(null)
        }

        override fun onMessage(stream: Stream, msg: ByteBuf) {
            val msgStr = msg.toString(Charset.defaultCharset())
            LogUtils.dTag(TAG, "onMessage: $msgStr")
            chatCallback(stream.remotePeerId(), msgStr)
        }

        override fun send(message: String) {
            LogUtils.dTag(TAG, "send: $message")
            val data = message.toByteArray(Charset.defaultCharset())
            stream.writeAndFlush(data.toByteBuf())
        }
    }
}
