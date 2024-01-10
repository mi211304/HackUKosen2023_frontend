package com.livinideas.testmap

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.json.FuelJson
import com.github.kittinunf.fuel.json.responseJson
import org.json.JSONObject
import com.github.kittinunf.result.Result
import java.io.FileOutputStream

class LogInActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)


        supportActionBar?.hide()
        // ボタン要素の取得
        var signUpButton: Button = findViewById(R.id.signUpButton)
        signUpButton.text = "ログイン"


        // EditText要素の取得
        val usernameEditText: EditText = findViewById(R.id.usernameEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)


        // ボタンが押された時の処理
        signUpButton.setOnClickListener {
            //バックエンドとのやりとり
            val url = "url"
            val header = hashMapOf("token" to "")
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()

            performGetRequest(url, header, "{\"user_name\" : \"$username\", \"password\" : \"$password\"}") { data ->
                pickTokenData(data)
            }
        }
    }

    // リクエストを実行する関数
    fun performGetRequest(url: String, header: Map<String, String>, jsonBody : String, onResponse: (JSONObject) -> Unit) {
        Fuel.post(url)
            .header(header)
            .jsonBody(jsonBody)
            .responseJson { _, _, result ->
                handleJsonResponse(result, onResponse)
            }
    }

    // JSONレスポンスを処理する共通関数
    fun handleJsonResponse(result: Result<FuelJson, FuelError>, callback: (JSONObject) -> Unit) {
        when (result) {
            is Result.Failure -> {
                val ex = result.getException()
                println(ex)
            }

            is Result.Success -> {
                val data = result.get().obj()
                callback(data)
            }
        }
    }

    fun pickTokenData(data : JSONObject) {
        val token = data.getString("token")
        Log.d("Tokens", token)

        // トークンをファイルに保存
        saveTokenToFile(token)
    }

    private fun saveTokenToFile(token: String) {
        try {
            // ファイルの書き込みモードを指定
            val outputStream: FileOutputStream = openFileOutput("token.txt", Context.MODE_PRIVATE)
            // トークンをファイルに書き込む
            outputStream.write(token.toByteArray())
            outputStream.close()
            Log.d("Tokenstringpower", "Token saved to file: $token")

        } catch (e: Exception) {
            Log.e("Tokenstringpower", "Error writing token to file: $e")

        }
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
}