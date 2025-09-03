package com.pengbo.p2pchat

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pengbo.p2pchat.chat.ChatNode
import java.util.concurrent.CompletableFuture.runAsync

class P2PChatActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "P2PChatActivity"

        fun start(context: Context) {
            val intent = Intent(context, P2PChatActivity::class.java)
            context.startActivity(intent)
        }
    }

    private lateinit var chatScroller: ScrollView
    private lateinit var chatWindow: TextView
    private lateinit var line: EditText
    private lateinit var sendButton: Button
    private lateinit var multicastLock: WifiManager.MulticastLock
    private lateinit var backButton: Button
    private lateinit var addPeerButton: Button

    private var chatNode: ChatNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_p2pchat)

        chatScroller = findViewById(R.id.chatScroll)
        chatWindow = findViewById(R.id.chat)
        line = findViewById(R.id.line)
        sendButton = findViewById(R.id.send)
        backButton = findViewById(R.id.btn_back)
        addPeerButton = findViewById(R.id.btn_add_peer)

        sendButton.setOnClickListener { sendText() }
        backButton.setOnClickListener { finish() }
        addPeerButton.setOnClickListener { }

        runAsync {
            acquireMulticastLock()

            chatNode = ChatNode(::chatMessage)
            chatMessage("\nLibp2p Chatter!\n=============\n")
            chatMessage("This node is ${chatNode?.peerId}, listening on ${chatNode?.address}\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        releaseMulticastLock()
        chatNode?.stop()
    } // onDestroy

    private fun sendText() {
        val msg = line.text.toString().trim()
        if (msg.isEmpty())
            return

        // send message here
        chatNode?.send(msg)

        chatMessage("You > " + msg)

        line.text.clear()
    } // sendText

    private fun chatMessage(msg: String) {
        runOnUiThread {
            chatWindow.append(msg)
            chatWindow.append("\n")
            chatScroller.post { chatScroller.fullScroll(View.FOCUS_DOWN) }
        }
    } // chatMessage

    private fun acquireMulticastLock() {
        val wifi = getSystemService(WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("libp2p-chatter")
        multicastLock.acquire()
    }

    private fun releaseMulticastLock() {
        multicastLock.release()
    }
}