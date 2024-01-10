package com.livinideas.testmap

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.json.FuelJson
import com.github.kittinunf.fuel.json.responseJson
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_start.start_button
import org.json.JSONObject
import com.github.kittinunf.result.Result
import kotlinx.android.synthetic.main.activity_start.ranking_1_name_text
import kotlinx.android.synthetic.main.activity_start.ranking_1_result_text
import kotlinx.android.synthetic.main.activity_start.ranking_2_name_text
import kotlinx.android.synthetic.main.activity_start.ranking_2_result_text
import kotlinx.android.synthetic.main.activity_start.ranking_3_name_text
import kotlinx.android.synthetic.main.activity_start.ranking_3_result_text
import kotlinx.android.synthetic.main.activity_start.ranking_4_name_text
import kotlinx.android.synthetic.main.activity_start.ranking_4_result_text
import kotlinx.android.synthetic.main.activity_start.ranking_5_name_text
import kotlinx.android.synthetic.main.activity_start.ranking_5_result_text
import java.io.BufferedReader
import java.io.InputStreamReader


class MainActivity : AppCompatActivity() {

    private var header = mapOf("Authorization" to "")

    private fun readTokenFromFile(): Map<String, String> {
        try {
            // ファイルからトークンを読み取る
            val inputStream = openFileInput("token.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val token = reader.readLine()

            Log.d("Tokenstringpower", "Token read from file: $token")

            header = mapOf("Authorization" to token)
            Log.d("Tokenstringpower", header.toString())
        } catch (e: Exception) {
            Log.e("Tokenstringpower", "Error reading token from file: $e")
        }

        // エラー時は空のヘッダーを返す
        return emptyMap()
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        supportActionBar?.hide()

        readTokenFromFile()
        val url = "url"
        val cities = listOf("東京")   //バグ対策いつか解決したい

        // ランキングデータを取得
        performGetRequest(url, header, "{ \"cities\" : $cities }") { data ->
            pickRankingData(data)
        }

        // ボタンクリック時の処理を設定
        start_button.setOnClickListener {
            // SantaGameActivity を起動
            val intent = Intent(this, SantaGameActivity::class.java)
            startActivity(intent)
        }
    }

    // リクエストを実行する関数
    fun performGetRequest(url: String, header: Map<String, String>, jsonBody : String, onResponse: (JSONObject) -> Unit) {
        Fuel.get(url)
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

    @SuppressLint("SetTextI18n")
    fun pickRankingData(data: JSONObject) {
        val array = data.getJSONArray("ranking")

        // RankingItemクラスを使ったリストを初期化
        val rankingList = mutableListOf<RankingItem>()

        // 配列内の各オブジェクトにアクセスする例
        for (i in 0 until array.length()) {
            val ranObject = array.getJSONObject(i)
            val name = ranObject.getString("user_name")
            val score = ranObject.getInt("score")

            // RankingItemにデータを格納してリストに追加
            val rankingItem = RankingItem(name, score)
            rankingList.add(rankingItem)

            // Log the information
            Log.d("ResponseBody", "Name: $name, Score: $score")

            // リストのサイズが5以上になったらdisplayRankingDataを呼び出す
            Handler(Looper.getMainLooper()).postDelayed({
                displayRankingData(rankingList)
            }, 1000)
        }
    }

    // RankingItemデータクラス
    data class RankingItem(val name: String, val score: Int)

    // 上位5人の情報をTextViewに表示する関数
    private fun displayRankingData(rankingList: List<RankingItem>) {
        Log.d("ResponseBody", rankingList.toString())
        Log.d("ResponseBody", "ok")
        ranking_1_name_text.text = rankingList[0].name
        ranking_1_result_text.text = rankingList[0].score.toString()
        ranking_2_name_text.text = rankingList[1].name
        ranking_2_result_text.text = rankingList[1].score.toString()
        ranking_3_name_text.text = rankingList[2].name
        ranking_3_result_text.text = rankingList[2].score.toString()
        ranking_4_name_text.text = rankingList[3].name
        ranking_4_result_text.text = rankingList[3].score.toString()
        ranking_5_name_text.text = rankingList[4].name
        ranking_5_result_text.text = rankingList[4].score.toString()
    }
}
