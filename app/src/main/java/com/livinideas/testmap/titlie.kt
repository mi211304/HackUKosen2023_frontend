package com.livinideas.testmap

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_titlie)
        supportActionBar?.hide()

        // 上部のボタン
        val signUpButton: View = findViewById(R.id.signUpButton)
        signUpButton.setOnClickListener {
            // SignUpActivityを呼び出す
            val intent = Intent(this, SignUpActivity::class.java)
            startActivity(intent)
        }

        // 下部のボタン
        val logInButton: View = findViewById(R.id.logInButton)
        logInButton.setOnClickListener {
            // LogInActivityを呼び出す
            val intent = Intent(this, LogInActivity::class.java)
            startActivity(intent)
        }
    }
}
