package com.pengbo.p2pchat

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var p2pTestButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        p2pTestButton = findViewById(R.id.btn_p2p_test)
        p2pTestButton.setOnClickListener { P2PChatActivity.start(this) }
    }
}