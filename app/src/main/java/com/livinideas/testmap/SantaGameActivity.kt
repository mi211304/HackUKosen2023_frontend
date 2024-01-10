package com.livinideas.testmap

import kotlinx.serialization.json.Json
import android.Manifest
import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.json.FuelJson
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import kotlinx.android.synthetic.main.activity_main.result_bonus_score_text
import kotlinx.android.synthetic.main.activity_main.result_score_text
import kotlinx.android.synthetic.main.activity_main.result_time_text
import kotlinx.android.synthetic.main.activity_main.result_total_score_text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Random
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.serialization.encodeToString


class SantaGameActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var destinationLatLng: LatLng? = null
    private val maxWaypoints = 3 // 中間地点の最大数
    private var isWaypointSelected = false
    private val waypoints: MutableList<LatLng> = mutableListOf()
    private val waypointMarkers: MutableList<Marker> = mutableListOf()
    private val decodedPolylinePoints: MutableList<List<LatLng>> = mutableListOf()
    private val latLngOrigin = LatLng(66.62765819451323, 25.81479157532732) //出発地点
    private val apiKey = ""//apiキー
    private var movingMarker: Marker? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val imageResources = arrayOf(R.drawable.three, R.drawable.second, R.drawable.first)
    private val pinResources = arrayOf(R.drawable.pin1, R.drawable.pin2, R.drawable.pin3)
    private var santaResources: BitmapDescriptor? = null
    private var currentImageIndex = 0
    private var imageHandler: Handler? = null
    private var getPartialRouteCount = 0
    private var checkeresult = 0

    //アニメーション
    private val ANIMATION_FRAME_INTERVAL = 500 // フレームのインターバル（ミリ秒）
    private val ANIMATION_DISTANCE_INTERVAL = 60// 等間隔の距離（メートル）
    private var movingMarkerCircle: Circle? = null

    //サークル
    private val MOVING_MARKER_CIRCLE_RADIUS = 600000.0 //サークルの大きさ
    private var movingMarkerCircleOptions: CircleOptions? = null

    //カウントダウン
    private var countdownTimer: CountDownTimer? = null
    private var countdownTextView: TextView? = null
    private val COUNTDOWN_DURATION = 60000 // カウントダウンの総時間（ミリ秒）
    private var countdownTimeRemaining = COUNTDOWN_DURATION.toLong()
    private var secondsRemaining = 60

    //アニメーションの速度を無理やり決める
    // アニメーションの総距離（メートル）
    private var totalDistance: Long = 0
    // 希望する速度をミリ秒毎のメートルで設定
    private val desiredSpeed = 620.0
    // 期間を希望する速度と総距離に基づいて計算
    private var ANIMATION_DURATION_MS = (totalDistance / desiredSpeed).toLong()


    //都市のピン
    private var isMapReady = false
    private val pinsList: MutableList<PinData> = mutableListOf() //都市情報
    data class PinData(val title: String, val position: LatLng, var isVisible: Boolean = false)
    private val pinImages = listOf(R.drawable.rpin1, R.drawable.rpin2, R.drawable.rpin3) //都市のpin画像


    //中間地点と目的地がの通過確認用変数
    private var checkWayPoints = 0
    
    //タイムポイント
    private var timePointText = ""
    private var timePoint = 0

    //ベースポイント
    private var basePoint = 0

    //ボーナスポイント
    private var tapPins = 0
    private var bonusPoint = 0

    //トータルポイント
    private var totalPoint = 0

    //通信用
    private var url = "URL"
    private var header = mapOf("Authorization" to "")


    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(gMap: GoogleMap) {
        googleMap = gMap
        isMapReady = true

        // マーカーがタップされたときのリスナーを設定
        googleMap?.setOnMarkerClickListener { marker ->
            // タップされたマーカーの情報を取得
            val tappedPinTitle = marker.title

            // マーカーの画像を変更する処理を実装
            changePinImage(marker, tappedPinTitle)

            marker.showInfoWindow()
            true
        }

    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.fragment_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // マップが完全に準備されるまで待つ
        mapFragment.view?.viewTreeObserver?.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    // マップが完全に準備された後の処理をここに移動
                    readAndAddPinsFromTxtFile()
                    makeSantaResources()
                    readTokenFromFile()

                    // リスナーの削除（1回だけ実行するため）
                    mapFragment.view?.viewTreeObserver?.removeOnGlobalLayoutListener(this)
                }
            }
        )
        supportActionBar?.hide()
    }

    @Suppress("UNREACHABLE_CODE")
    private fun readTokenFromFile(): Map<String, String> {
        try {
            // ファイルからトークンを読み取る
            val inputStream = openFileInput("token.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val token = reader.readLine()

            Log.d("Tokenstringpower", "Token read from file: $token")

            // トークンをヘッダーに設定
            header = mapOf("Authorization" to token)
        } catch (e: Exception) {
            Log.e("Tokenstringpower", "Error reading token from file: $e")
        }

        // エラー時は空のヘッダーを返す
        return emptyMap()
    }


    private fun readAndAddPinsFromTxtFile() {
        try {
            // assetsフォルダ内のtxtファイルを開く
            val inputStream: InputStream = assets.open("pins.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))

            // ファイルを行ごとに読み込み、ピンの情報を保持
            reader.forEachLine { line ->
                Log.d("MyApp", "Read line: $line")
                val parts = line.split(":")
                if (parts.size == 2) {
                    val pinName = parts[0].trim()
                    val coordinates = parts[1].trim().split(",")
                    if (coordinates.size == 2) {
                        val latitude = coordinates[0].toDouble()
                        val longitude = coordinates[1].toDouble()

                        // ピンの情報を保持
                        val pin = PinData(pinName, LatLng(latitude, longitude))
                        pinsList.add(pin)
                    }
                }
            }

            // リーダーを閉じる
            reader.close()
            showLocationDialog()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    private fun showLocationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("現在地を取得しますか？")
            .setPositiveButton("OK", DialogInterface.OnClickListener { dialog, id ->
                requestLocationUpdates()
            })

        builder.setCancelable(false)

        builder.create().show()
    }

    private fun requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                // 現在の位置をゴール地点に設定
                destinationLatLng = LatLng(location.latitude, location.longitude)

                updateMapWithDirections()

                showLocationDecisionButton()
            }
        }
    }

    private fun showLocationDecisionButton(){
        val decisionButton = findViewById<Button>(R.id.decision_button)

        // クリックリスナーを設定
        decisionButton.setOnClickListener {
            showWaypointSelectionDialog()
            decisionButton.visibility = View.GONE
        }

        // ボタンを表示
        decisionButton.visibility = View.VISIBLE
    }


    private fun showWaypointSelectionDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("中間地点を選択してください")
            .setPositiveButton("OK") { dialog, id ->
                enableWaypointSelection()
            }

        builder.setCancelable(false)

        builder.create().show()
    }

    @SuppressLint("PotentialBehaviorOverride")
    private fun enableWaypointSelection() {
        googleMap?.setOnMapClickListener { latLng ->
            if (waypoints.size == maxWaypoints) {
                return@setOnMapClickListener
            }


            removeDefaultMarkers()

            //バグ対策(いつか直す)
            val pinDrawable = pinResources[0]
            val bitmap = BitmapFactory.decodeResource(resources, pinDrawable)
            val scaleFactor = 0.001f
            val newWidth = (bitmap.width * scaleFactor).toInt()
            val newHeight = (bitmap.height * scaleFactor).toInt()
            val scaledBitmapDescriptor = BitmapDescriptorFactory.fromBitmap(scaleBitmap(bitmap, newWidth, newHeight))

            val newWaypoint = googleMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("中間地点")
                    .draggable(true)
                    .icon(scaledBitmapDescriptor)//仮設置
            )
            newWaypoint?.let {
                waypointMarkers.add(it)
                waypoints.add(it.position)
            }

            // 決定ボタン表示
            showWaypointDecisionButton()

            // 一つ戻るボタン表示
            showWaypointBackButton()

            isWaypointSelected = true

            newWaypoint?.let {
                googleMap?.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
                    override fun onMarkerDragStart(marker: Marker) {
                    }

                    override fun onMarkerDrag(marker: Marker) {
                    }

                    override fun onMarkerDragEnd(marker: Marker) {
                    }
                })
            }
            updateMapWithDirections()
        }
    }


    private fun removeDefaultMarkers() {
        // デフォルトのマーカーをすべて削除
        googleMap?.clear()
        // 保存された座標情報をリセット
        decodedPolylinePoints.clear()
    }


    private fun disableWaypointSelection() {
        googleMap?.setOnMapClickListener(null)

        waypointMarkers.forEach { it.remove() }
        waypointMarkers.clear()
        isWaypointSelected = false

        // 決定ボタン非表示
        showWaypointDecisionButton()
    }

    private fun showWaypointDecisionButton() {
        val decisionButton = findViewById<Button>(R.id.decision_button)

        // クリックリスナーを設定
        decisionButton.setOnClickListener {
            if (isWaypointSelected) {
                if (waypoints.size == maxWaypoints ) {
                    // ウェイポイントの選択を無効化
                    disableWaypointSelection()
                    // ボタンを非表示にする
                    decisionButton.visibility = View.GONE
                    //一つ戻るボタンを非表示にする
                    closeWaypointBackButton()
                    //スタートボタンの表示
                    startGameButton()
                } else {
                    // ピンがまだ最大数に達していない場合はダイアログを表示
                    showIncompleteWaypointsDialog()
                }
            }
        }

        // ボタンを表示
        decisionButton.visibility = View.VISIBLE
    }

    private fun showIncompleteWaypointsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("中間地点を三か所選択してください。")
            .setPositiveButton("OK") { dialog, id ->
                // 中間地点選択画面に戻る
                enableWaypointSelection()
            }

        builder.setCancelable(false)

        builder.create().show()
    }


    private fun showWaypointBackButton() {
        val decisionButton = findViewById<Button>(R.id.back_button)

        // クリックリスナーを設定
        decisionButton.setOnClickListener {
            if (waypoints.isNotEmpty()) {
                waypoints.removeAt(waypoints.size - 1)
                //mapの更新
                updateMapWithDirections()
                //中間地点を決める処理に戻る
                enableWaypointSelection()
            }
        }

        // ボタンを表示
        decisionButton.visibility = View.VISIBLE
    }

    private fun closeWaypointBackButton(){
        val decisionButton = findViewById<Button>(R.id.back_button)
        decisionButton.visibility = View.GONE
    }


    private fun updateMapWithDirections() {
        //mapの初期化
        googleMap?.clear()
        // 保存された座標情報をリセット
        decodedPolylinePoints.clear()

        if (waypoints.isEmpty()) {
            // ウェイポイントがない場合は直接ゴールまでの経路を取得
            checkAndCallPartialRoute(latLngOrigin, destinationLatLng!!, apiKey)
        } else {
            // スタート地点からwaypointsの最初の地点までの経路を取得
            checkAndCallPartialRoute(latLngOrigin, waypoints[0], apiKey)

            // waypointsの各連続した地点間の経路を取得
            for (i in 0 until waypoints.size - 1) {
                checkAndCallPartialRoute(waypoints[i], waypoints[i + 1], apiKey)
            }

            // waypointsの最後の地点からゴール地点までの経路を取得
            checkAndCallPartialRoute(waypoints.last(), destinationLatLng!!, apiKey)
        }
    }

    private fun checkAndCallPartialRoute(origin: LatLng, destination: LatLng, apiKey: String) {
        if (getPartialRouteCount == 0) {
            getPartialRouteCount = 1
            getPartialRoute(origin, destination, apiKey)
        } else {
            // 0 になるまで待機してから再帰呼び出し
            val handler = Handler()
            handler.postDelayed({
                checkAndCallPartialRoute(origin, destination, apiKey)
            }, 100) // 100ms 待機して再試行
        }
    }

    private fun getPartialRoute(origin: LatLng, destination: LatLng, apiKey: String) {
        val urlDirections = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&mode=walking" +
                "&key=$apiKey"

        coroutineScope.launch {
            var loadingText = findViewById<ImageView>(R.id.loading_text)
            loadingText.visibility = View.VISIBLE
            try {
                // APIリクエストの結果を待機
                val response = withContext(Dispatchers.IO) {
                    makeApiRequest(urlDirections)
                }

                // 経路を取得した後の処理
                handlePartialRoute(response)
            } catch (e: Exception) {
                // エラーが発生した場合の処理
                Log.e("MyApp", "Error fetching route: ${e.message}")

                // ZERO_RESULTSの場合に「経路が見つかりませんでした」と表示
                if (e.message?.contains("ZERO_RESULTS") == true) {
                    runOnUiThread {
                        val toast =
                            Toast.makeText(this@SantaGameActivity, "経路が見つかりませんでした", Toast.LENGTH_LONG)
                        toast.show()

                        // 3秒後にToastメッセージを非表示にする
                        Handler(Looper.getMainLooper()).postDelayed({
                            toast.cancel()
                        }, 3000)
                    }
                } else {
                    // それ以外のエラーの場合はログに出力
                    Log.e("MyApp", "Unexpected error: ${e.message}")
                }
            } finally {
                // 処理が完了したら getPartialRouteCount を 0 に戻す
                getPartialRouteCount = 0
                loadingText.visibility = View.GONE
            }
        }

    }

    private suspend fun makeApiRequest(url: String): String {
        return suspendCoroutine { continuation ->
            val directionsRequest = object : StringRequest(
                Request.Method.GET, url,
                Response.Listener<String> { response ->
                    continuation.resume(response)
                },
                Response.ErrorListener {
                    continuation.resumeWithException(Exception("API request failed"))
                }) {}

            val requestQueue = Volley.newRequestQueue(this)
            requestQueue.add(directionsRequest)
        }
    }

    @Suppress("NAME_SHADOWING")
    private fun handlePartialRoute(response: String) {
        Log.d("MyApp", "Directions API レスポンス成功")

        // レスポンスを処理してマップを更新
        val jsonResponse = JSONObject(response)
        val routes = jsonResponse.getJSONArray("routes")


        // 出発地点とのマーカーを追加
        // ピンの画像を取得
        var pinDrawable = R.drawable.spin
        var bitmap = BitmapFactory.decodeResource(resources, pinDrawable)
        // 新しいサイズを計算
        var scaleFactor = 0.05f
        var newWidth = (bitmap.width * scaleFactor).toInt()
        var newHeight = (bitmap.height * scaleFactor).toInt()
        var scaledBitmapDescriptor = BitmapDescriptorFactory.fromBitmap(scaleBitmap(bitmap, newWidth, newHeight))

        googleMap?.addMarker(MarkerOptions()
            .position(latLngOrigin)
            .title("出発地点")
            .icon(scaledBitmapDescriptor)
        )

        // 出発地点とのマーカーを追加
        // ピンの画像を取得
        pinDrawable = R.drawable.homepin
        bitmap = BitmapFactory.decodeResource(resources, pinDrawable)
        // 新しいサイズを計算
        scaleFactor = 0.083f
        newWidth = (bitmap.width * scaleFactor).toInt()
        newHeight = (bitmap.height * scaleFactor).toInt()
        scaledBitmapDescriptor = BitmapDescriptorFactory.fromBitmap(scaleBitmap(bitmap, newWidth, newHeight))

        googleMap?.addMarker(MarkerOptions()
            .position(destinationLatLng!!)
            .title("現在地")
            .icon(scaledBitmapDescriptor)
        )

        waypoints.forEachIndexed { index, waypoint ->
            val pinIndex = index % pinResources.size // 中間地点ごとに異なるピン画像を使う
            val pinDrawable = pinResources[pinIndex]

            // ピンの画像を取得
            val bitmap = BitmapFactory.decodeResource(resources, pinDrawable)

            // ピンのサイズを変更
            val scaleFactor = 0.039f
            val newWidth = (bitmap.width * scaleFactor).toInt()
            val newHeight = (bitmap.height * scaleFactor).toInt()
            val scaledBitmapDescriptor = BitmapDescriptorFactory.fromBitmap(scaleBitmap(bitmap, newWidth, newHeight))

            googleMap?.addMarker(
                MarkerOptions()
                    .position(waypoint)
                    .title("中間地点${index + 1}")
                    .draggable(true)
                    .icon(scaledBitmapDescriptor)
                    .alpha(1.0f)
            )
        }

        Log.d("MyApp", "マーカーの追加完了")


        // カメラ位置の調整
        val builder = LatLngBounds.Builder()
        builder.include(destinationLatLng!!)
        builder.include(latLngOrigin)
        waypoints.forEach { builder.include(it) }
        waypointMarkers.forEach { builder.include(it.position) }
        val bounds = builder.build()
        val padding = 100
        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
        googleMap?.moveCamera(cameraUpdate)



        Log.d("MyApp", "カメラ位置の調整完了")

        // ルートの描画
        for (i in 0 until routes.length()) {
            val legs = routes.getJSONObject(i).getJSONArray("legs")

            for (k in 0 until legs.length()) {
                val steps = legs.getJSONObject(k).getJSONArray("steps")

                val path: MutableList<List<LatLng>> = ArrayList()
                for (j in 0 until steps.length()) {
                    val points =
                        steps.getJSONObject(j).getJSONObject("polyline").getString("points")
                    val decodedPoints = PolyUtil.decode(points)
                    path.add(decodedPoints)
                }

                decodedPolylinePoints.addAll(path)

                for (j in 0 until path.size) {
                    val points = steps.getJSONObject(j).getJSONObject("polyline").getString("points")
                    val decodedPoints = PolyUtil.decode(points)
                    if (decodedPoints != null) {
                        googleMap?.addPolyline(PolylineOptions().addAll(decodedPoints).color(Color.RED))
                    } else {
                        Log.e("MyApp", "ポイントがnullです。 points: $points")
                    }
                }
            }
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }



    private fun startGameButton() {
        val startButton = findViewById<Button>(R.id.start_button)

        // スタートボタンにクリックリスナーを設定
        startButton.setOnClickListener {
            Log.d("MyApp", "Start button clicked")
            //ボタン非表示
            startButton.visibility = View.GONE

            // カメラを出発地点周辺にズーム
            val startCameraUpdate = CameraUpdateFactory.newLatLngZoom(latLngOrigin, 5f)
            googleMap?.moveCamera(startCameraUpdate)

            // カウントダウン開始
            showCountdownImages()
        }

        // スタートボタンを表示
        startButton.visibility = View.VISIBLE
    }

    private fun showCountdownImages() {
        val imageView = findViewById<ImageView>(R.id.countdown_image)

        // カウントダウンを表示
        imageView.visibility = View.VISIBLE

        imageHandler = Handler()
        imageHandler?.postDelayed(object : Runnable {
            override fun run() {
                if (currentImageIndex < imageResources.size) {
                    imageView.setImageResource(imageResources[currentImageIndex])
                    currentImageIndex++
                    imageHandler?.postDelayed(this, 1000) // 1秒ごとに次の画像を表示
                } else {
                    // カウントダウンが終了したらカウントダウンの要素を非表示にしてピンのアニメーションを開始
                    imageView.visibility = View.GONE
                    animateMarker()
                    startCountdownTimer()
                }
            }
        }, 1000) // 最初の画像を1秒後に表示
    }

    //カウントダウン開始
    private fun startCountdownTimer() {
        val imageView = findViewById<ImageView>(R.id.illustration_image)
        countdownTextView = findViewById(R.id.countdown_text)

        // カウントダウンを表示
        imageView.visibility = View.VISIBLE
        countdownTextView!!.visibility = View.VISIBLE

        countdownTimer = object : CountDownTimer(countdownTimeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // カウントダウン中の秒数を表示
                countdownTimeRemaining = millisUntilFinished
                secondsRemaining = (millisUntilFinished / 1000).toInt()
                countdownTextView?.text = String.format("%02d", secondsRemaining)
            }

            override fun onFinish() {
                // カウントダウンが終了した時の処理
                imageView.visibility = View.GONE
                countdownTextView!!.visibility = View.GONE
                // ピンのアニメーションを停止
                stopPinAnimation()
                // 終了時のアクションを追加（例: ResultTimeOver.kt を呼び出す）
                resultTimeover()
            }
        }
        countdownTimer?.start()
    }

    private var smoothedPoints: List<LatLng>? = null

    // アニメーションの開始
    private fun animateMarker() {
        // 各経路を等間隔で分割したポイントを取得
        smoothedPoints = getSmoothedPoints()

        // ValueAnimator によるアニメーションの作成
        val valueAnimator = ValueAnimator.ofInt(0, smoothedPoints!!.size - 1)
        ANIMATION_DURATION_MS = (totalDistance / desiredSpeed).toLong()
        valueAnimator.duration = ANIMATION_DURATION_MS
        Log.d("ANIMATION_DURATION_MS", ANIMATION_DURATION_MS.toString())


        // アニメーションの更新時のリスナーを設定
        valueAnimator.addUpdateListener { animation ->
            val index = animation.animatedValue as Int
            // インデックスに基づいて、座標を計算してピンを移動
            moveMarkerToSmoothedPoint(smoothedPoints!![index])

            // ピンの位置にカメラを追従させる
            movingMarker?.position?.let { position ->
                val cameraUpdate = CameraUpdateFactory.newLatLng(position)
                googleMap?.moveCamera(cameraUpdate)
            }

            // アニメーション中に描画される円を更新
            updateMovingMarkerCircle(smoothedPoints!![index])
            //中間地点と目的地が円の中に入ったか確認
            checkWaypointsAndDestinationInsideCircle()
        }

        // アニメーションの終了時のリスナーを設定
        valueAnimator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animator: Animator) {
                // アニメーションが開始した時の処理
            }

            override fun onAnimationEnd(animator: Animator) {
                // アニメーションが終了した時の処理
                if (checkeresult == 0) {
                    resultTrue()
                    countdownTimer?.cancel()
                }
            }

            override fun onAnimationCancel(animator: Animator) {
                // アニメーションがキャンセルされた時の処理
            }

            override fun onAnimationRepeat(animator: Animator) {
                // アニメーションが繰り返された時の処理
            }
        })

        // アニメーションの開始
        valueAnimator.start()
    }

    private fun getSmoothedPoints(): List<LatLng> {
        val smoothedPoints = mutableListOf<LatLng>()

        // 各経路に対して処理
        for (path in decodedPolylinePoints) {
            for (i in 0 until path.size - 1) {
                val startPoint = path[i]
                val endPoint = path[i + 1]
                val distance = SphericalUtil.computeDistanceBetween(startPoint, endPoint)
                 totalDistance += distance.toLong()

                // 60mおきにポイントを追加
                val numPoints = (distance / ANIMATION_DISTANCE_INTERVAL).toInt()  // 60mおきにポイントを生成
                val fractionInterval = 1.0 / numPoints

                for (j in 0 until numPoints) {
                    val fraction = j * fractionInterval
                    val intermediatePoint = SphericalUtil.interpolate(startPoint, endPoint, fraction)
                    smoothedPoints.add(intermediatePoint)
                }
            }
        }

        return smoothedPoints
    }


    // アニメーションフレームごとに呼ばれる関数
    private fun moveMarkerToSmoothedPoint(latLng: LatLng) {
        movingMarker?.remove() // 既存のマーカーが存在する場合、前のマーカーを削除
        movingMarker = googleMap?.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("サンタクロース")
                .icon(santaResources)
        )
        updateMovingMarkerCircle(latLng)

        movingMarker?.let {
            // 既存のマーカーが存在する場合は、新しい位置にアニメーションで移動させる
            it.animateMarkerToPosition(latLng, ANIMATION_FRAME_INTERVAL.toLong())

            // 現在の座標が円の中に入っているかチェック
            if (isLatLngInsideCircle(latLng, movingMarkerCircleOptions?.center, MOVING_MARKER_CIRCLE_RADIUS)) {
                // 円の中に入っている場合はピンを表示
                showPinsInsideCircle(latLng)
            }
        }
    }

     // リクエストを実行する関数
    private fun performGetRequest(url: String, header: Map<String, String>, jsonBody : String, onResponse: (JSONObject) -> Unit) {
        Fuel.post(url)
            .header(header)
            .jsonBody(jsonBody)
            .responseJson { _, _, result ->
                handleJsonResponse(result, onResponse)
            }

    }

    // JSONレスポンスを処理する共通関数
    private fun handleJsonResponse(result: Result<FuelJson, FuelError>, callback: (JSONObject) -> Unit) {
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

    private fun pickScoreData (data : JSONObject) {
        data.getInt("score")
        basePoint += data.getInt("score")
    }

    // アニメーション中に中間地点と目的地が円の中に入ったか確認
    private fun checkWaypointsAndDestinationInsideCircle() {
        // アニメーション中の現在の位置を取得
        val currentLatLng = movingMarker?.position

        // 中間地点1が円の中に入ったか確認
        if (secondsRemaining != 0){
            if (checkWayPoints == 0 && currentLatLng != null) {
                if (isLatLngInsideCircle(currentLatLng, waypoints[0], MOVING_MARKER_CIRCLE_RADIUS)) {
                    Log.d("MyApp", "中間地点1通過")
                    checkWayPoints += 1
                    Log.d("pinListsName1", pinListsName[0].toString())

                    val citiesJson = Json.encodeToString(pinListsName[0])
                    Log.d("pinListsName1", citiesJson)

                    performGetRequest(url, header, "{\"cities\" : $citiesJson}") { data ->
                        pickScoreData(data)
                    }

                }
            }
        }

        // 中間地点2が円の中に入ったか確認
        if (secondsRemaining != 0){
            if (checkWayPoints == 1 && currentLatLng != null) {
                if (isLatLngInsideCircle(currentLatLng, waypoints[1], MOVING_MARKER_CIRCLE_RADIUS)) {
                    Log.d("MyApp", "中間地点2通過")
                    checkWayPoints += 1
                    Log.d("pinListsName2", pinListsName[1].toString())

                    val citiesJson = Json.encodeToString(pinListsName[1])
                    Log.d("pinListsName1", citiesJson)

                    performGetRequest(url, header, "{\"cities\" : $citiesJson}") { data ->
                        pickScoreData(data)
                    }
                }
            }
        }

        // 中間地点3が円の中に入ったか確認
        if (secondsRemaining != 0){
            if (checkWayPoints == 2 && currentLatLng != null) {
                if (isLatLngInsideCircle(currentLatLng, waypoints[2], MOVING_MARKER_CIRCLE_RADIUS)) {
                    Log.d("MyApp", "中間地点3通過")
                    checkWayPoints += 1
                    Log.d("pinListsName3", pinListsName[2].toString())

                    val citiesJson = Json.encodeToString(pinListsName[2])
                    Log.d("pinListsName1", citiesJson)

                    performGetRequest(url, header, "{\"cities\" : $citiesJson}") { data ->
                        pickScoreData(data)
                    }
                }
            }
        }

        // 目的地が円の中に入ったか確認
        if (secondsRemaining != 0){
            if (checkWayPoints == 3 && currentLatLng != null) {
                if (isLatLngInsideCircle(currentLatLng, destinationLatLng, MOVING_MARKER_CIRCLE_RADIUS)) {
                    Log.d("MyApp", "目的地通過")
                    checkWayPoints += 1
                    Log.d("pinListsName4", pinListsName[3].toString())

                    val citiesJson = Json.encodeToString(pinListsName[3])
                    Log.d("pinListsName1", citiesJson)

                    performGetRequest(url, header, "{\"cities\" : $citiesJson}") { data ->
                        pickScoreData(data)
                    }
                }
            }
        }
    }


    // 円の中に座標が入っているかを判定する関数
    private fun isLatLngInsideCircle(point: LatLng, circleCenter: LatLng?, circleRadius: Double): Boolean {
        return SphericalUtil.computeDistanceBetween(circleCenter, point) <= circleRadius
    }

    // 円の中に入ったピンを表示する関数
    private fun showPinsInsideCircle(centerLatLng: LatLng) {
        for (pin in pinsList) {
            // pinsList に含まれるピンの座標が円の中に入っている場合
            if (isLatLngInsideCircle(pin.position, centerLatLng, MOVING_MARKER_CIRCLE_RADIUS)) {
                // まだ表示されていない場合に表示する
                if (!pin.isVisible) {
                    pin.isVisible = true
                    addCityPinToMap(pin)
                }
            } else {
                // 円の外に出たピンは非表示にする
                pin.isVisible = false
            }
        }
    }




    //円の中に入った都市を表示する
    private fun addCityPinToMap(pin: PinData) {
        if (googleMap == null) {
            Log.e("MyApp", "GoogleMap が null です")
            return
        }


        // ランダムに画像を選択
        val random = Random()
        val randomPinImage = pinImages[random.nextInt(pinImages.size)]

        val bitmap = BitmapFactory.decodeResource(resources, randomPinImage)

        // 新しいサイズを計算
        val scaleFactor = 0.05f
        val newWidth = (bitmap.width * scaleFactor).toInt()
        val newHeight = (bitmap.height * scaleFactor).toInt()

        // ピンのサイズを変更
        val scaledBitmapDescriptor =
            BitmapDescriptorFactory.fromBitmap(scaleBitmap(bitmap, newWidth, newHeight))

        val pinTitle = pin.title

        // ピンの名前がすでにリストに存在するか確認
        if (!isPinNameAlreadyExists(pinTitle)) {
            // ピンの名前が存在しない場合にのみピンを立て、リストに追加
            googleMap?.addMarker(
                MarkerOptions()
                    .position(pin.position)
                    .title(pinTitle)
                    .icon(scaledBitmapDescriptor)
            )

            Log.d("MyApp", "$pinTitle のマーカーを ${pin.position} に追加しました")

            //タイマーが0ではない間だけ追加する
            if (secondsRemaining != 0) {
                when (checkWayPoints) {
                    0 -> pinListsName[0].add(pinTitle)
                    1 -> pinListsName[1].add(pinTitle)
                    2 -> pinListsName[2].add(pinTitle)
                    3 -> pinListsName[3].add(pinTitle)
                }
            }
        }
    }

    // ピンの名前がすでにリストに存在するか確認する関数
    private fun isPinNameAlreadyExists(pinName: String): Boolean {
        for (list in pinListsName) {
            if (list.contains(pinName)) {
                return true
            }
        }
        return false
    }

    private val pinListsName: MutableList<MutableList<String>> = mutableListOf(
        mutableListOf(), // リスト1
        mutableListOf(), // リスト2
        mutableListOf(), // リスト3
        mutableListOf()  // リスト4
    )

    // マーカーのアニメーションを行う拡張関数
    private fun Marker.animateMarkerToPosition(targetPosition: LatLng, duration: Long) {
        val startPosition = position
        val interpolator = LinearInterpolator()
        val handler = Handler()

        val start = SystemClock.uptimeMillis()
        val runnable = object : Runnable {
            override fun run() {
                val elapsed = SystemClock.uptimeMillis() - start
                val t = interpolator.getInterpolation(elapsed.toFloat() / duration)
                val lng = t * targetPosition.longitude + (1 - t) * startPosition.longitude
                val lat = t * targetPosition.latitude + (1 - t) * startPosition.latitude
                position = LatLng(lat, lng)

                if (t < 1.0) {
                    // アニメーションが完了していない場合は再度ハンドラを呼び出す
                    handler.postDelayed(this, 30)
                }
            }
        }

        handler.post(runnable)
    }

    private fun updateMovingMarkerCircle(centerLatLng: LatLng) {
        // 円が既に描画されている場合は削除
        movingMarkerCircle?.remove()

        // CircleOptions が初めて作成されるか、アニメーション中に変更される場合
        if (movingMarkerCircleOptions == null) {
            // 初回のみ CircleOptions を作成
            movingMarkerCircleOptions = CircleOptions()
                .center(centerLatLng)
                .radius(MOVING_MARKER_CIRCLE_RADIUS)
                .strokeWidth(0f) // ふちの幅を 0 に設定（透明にする）
                .fillColor(Color.argb(0, 0, 0, 0)) // 円の中の色と透明度
        } else {
            // アニメーション中に変更がある場合は中心座標を更新
            movingMarkerCircleOptions?.center(centerLatLng)
            movingMarkerCircleOptions?.strokeWidth(0f) // ふちの幅を 0 に設定（透明にする）
            movingMarkerCircleOptions?.fillColor(Color.argb(0, 0, 0, 0)) // 透明にする
        }

        // 円を描画
        movingMarkerCircle = googleMap?.addCircle(movingMarkerCircleOptions!!)
    }

    private fun makeSantaResources(){
        val pinDrawable = R.drawable.santa

        val bitmap = BitmapFactory.decodeResource(resources, pinDrawable)

        // 新しいサイズを計算
        val scaleFactor = 0.085f
        val newWidth = (bitmap.width * scaleFactor).toInt()
        val newHeight = (bitmap.height * scaleFactor).toInt()

        // ピンのサイズを変更
        santaResources = BitmapDescriptorFactory.fromBitmap(scaleBitmap(bitmap, newWidth, newHeight))

    }

    // ピンのアニメーションを停止する関数
    private fun stopPinAnimation() {
        imageHandler?.removeCallbacksAndMessages(null) // ハンドラーのコールバックを削除
        movingMarker?.remove() // マーカーを削除
        movingMarkerCircle?.remove() // 円を削除
    }

    @SuppressLint("SetTextI18n")
    private fun resultTimeover(){

        val timeUpImage = findViewById<ImageView>(R.id.time_up_image)
        timeUpImage.setImageResource(R.drawable.timeup)

        //resorutTrueが呼び出されないようにする
        checkeresult = 1

        timePointText  = "時間切れ-50%"

        bonusPoint = tapPins * 200

        totalPoint = (basePoint + bonusPoint) / 2

        //残りの点数を確認する
        when (checkWayPoints) {
            0 -> Log.d("pinListsName1", pinListsName[0].toString())
            1 -> Log.d("pinListsName2", pinListsName[1].toString())
            2 -> Log.d("pinListsName3", pinListsName[2].toString())
            3 -> Log.d("pinListsName4", pinListsName[3].toString())
        }
        Log.d("MyApp", "APIレスポンス開始")
        Log.d("MyApp", "トータルポイントは $totalPoint")

        url = "url"

        performGetRequest(url, header, "{\"score\" : $totalPoint}") { data ->
            pickResultData(data)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            timeUpImage.visibility = View.GONE
            resultfin()
        }, 3000)
    }

    private fun resultTrue(){

        //残りタイムポイント
        if (secondsRemaining == 0){
            timePoint = 3000
            timePointText = "残り$secondsRemaining : $timePoint"
        }
        else if (secondsRemaining in 1..2){
            timePoint = 2500
            timePointText = "残り$secondsRemaining : $timePoint"
        }
        else if (secondsRemaining in 3..4){
            timePoint = 2000
            timePointText = "残り$secondsRemaining : $timePoint"
        }
        else if (secondsRemaining in 5..6){
            timePoint = 1500
            timePointText = "残り$secondsRemaining : $timePoint"
        }
        else if (secondsRemaining in 7..8){
            timePoint = 1000
            timePointText = "残り$secondsRemaining : $timePoint"
        }
        else if (secondsRemaining in 9..10){
            timePoint = 500
            timePointText = "残り$secondsRemaining : $timePoint"
        }
        else {
            timePoint = 300
            timePointText = "残り$secondsRemaining : $timePoint"
        }

        bonusPoint = tapPins * 200

        totalPoint = (basePoint + bonusPoint + timePoint)

        Log.d("MyApp", "APIレスポンス開始")
        Log.d("MyApp", "トータルポイントは $totalPoint")

        url = "url"

        performGetRequest(url, header, "{\"score\" : $totalPoint}") { data ->
            pickResultData(data)
        }

        resultfin()
    }

    private fun pickResultData(data : JSONObject) {
        Log.d("Result", data.getBoolean("isUpdateHighScore").toString())
    }

    private fun resultfin(){
        val resultImage = findViewById<ImageView>(R.id.result_image)
        val resultTimeText = findViewById<TextView>(R.id.result_time_text)
        val resultScoreText = findViewById<TextView>(R.id.result_score_text)
        val resultBonusScoreText = findViewById<TextView>(R.id.result_bonus_score_text)
        val resultTotalScoreText = findViewById<TextView>(R.id.result_total_score_text)
        val decisionButton = findViewById<Button>(R.id.result_button)

        //画面の更新
        result_time_text.text = timePointText
        result_score_text.text = basePoint.toString()
        result_bonus_score_text.text = bonusPoint.toString()
        result_total_score_text.text = totalPoint.toString()

        // クリックリスナーを設定
        decisionButton.setOnClickListener {
            decisionButton.visibility = View.GONE
            gamefin()
        }
        // リザルト画面を表示
        resultImage.visibility = View.VISIBLE
        resultTimeText.visibility = View.VISIBLE
        resultScoreText.visibility = View.VISIBLE
        resultBonusScoreText.visibility = View.VISIBLE
        resultTotalScoreText.visibility = View.VISIBLE
        decisionButton.visibility = View.VISIBLE
    }

    private fun gamefin(){
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }

    //都市ピンがタップされたときのピン表示の変更
    private fun changePinImage(marker: Marker, pinTitle: String?) {
        if (secondsRemaining != 0) {
            if (pinTitle != null && !listOf("出発地点", "現在地", "中間地点1", "中間地点2", "中間地点3", "サンタクロース", "中間地点", "中間地点１", "中間地点２", "中間地点３").contains(pinTitle)) {

                //ボーナス加点
                this.tapPins += 1
                Log.d("MyApp", tapPins.toString())

                // 新しいピンの画像を設定
                val newPinDrawable = R.drawable.chengpin
                val newBitmap = BitmapFactory.decodeResource(resources, newPinDrawable)

                // 新しいサイズを計算
                val scaleFactor = 0.1f
                val newWidth = (newBitmap.width * scaleFactor).toInt()
                val newHeight = (newBitmap.height * scaleFactor).toInt()

                // ピンのサイズを変更
                val newScaledBitmapDescriptor =
                    BitmapDescriptorFactory.fromBitmap(scaleBitmap(newBitmap, newWidth, newHeight))

                // ピンのアイコンを変更
                marker.setIcon(newScaledBitmapDescriptor)

                vibrateForHalfSecond()
            }
        }
    }

    //ゲーム中ボーナスポイントをタップした時に振動
    private fun vibrateForHalfSecond() {
        val vibrator: Vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Android 26以降、振動の許可が必要
        if (Build.VERSION.SDK_INT >= 26) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.VIBRATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            vibrator.vibrate(500)
        }
    }

}


