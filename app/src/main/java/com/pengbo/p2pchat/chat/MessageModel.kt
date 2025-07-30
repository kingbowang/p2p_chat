package com.pengbo.p2pchat.chat

data class MessageModel(
    var action: String = "",
    var ip: String = "",
    var port: Int = 0,
    var content: String = ""
)
