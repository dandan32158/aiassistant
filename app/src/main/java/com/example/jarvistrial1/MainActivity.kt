package com.example.jarvistrial1

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.util.*
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.util.Log
import okhttp3.MediaType
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class MainActivity : Activity() {

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var porcupineManager: PorcupineManager
    private val PORCUPINE_ACCESS_KEY = "CREATE A PORCUPINE ACCOUNT HERE FOR THE WAKE WORD"
    private val PERMISSIONS_REQUEST_RECORD_AUDIO = 2
    private val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }

    private val httpClient = OkHttpClient()
    private val GPT_API_URL = "YOUR END POINT URL HERE"
    private val GPT_API_KEY = "GPT4 API KEY HERE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                Log.i("TTS", "Text-to-Speech engine initialized successfully")
            } else {
                Log.e("TTS", "Error initializing Text-to-Speech engine: $status")
            }
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(bundle: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(v: Float) {}

            override fun onBufferReceived(bytes: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {}

            override fun onResults(results: Bundle?) {
                val recognizedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)
                recognizedText?.let {
                    askGPT4(it)
                }
            }

            override fun onPartialResults(bundle: Bundle?) {}

            override fun onEvent(i: Int, bundle: Bundle?) {}
        })

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
        } else {
            initializePorcupine()
        }
    }

    private fun initializePorcupine() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(PORCUPINE_ACCESS_KEY)
                .setKeywordPath("DOWNLOAD THE KEYWORD FILE YOU PICKED, IT SHOULD BE .PPN FILE")
                .setSensitivity(0.5f)
                .build(
                    this,
                    PorcupineManagerCallback { keywordIndex ->
                        if (keywordIndex == 0) {
                            speechRecognizer.startListening(speechRecognizerIntent)
                        }
                    }
                )
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing PorcupineManager", e)
        }
    }

    private fun askGPT4(inputText: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val jsonRequest = JSONObject()
                .put("prompt", inputText)
                .put("max_tokens", 150)

            val requestBody = RequestBody.create("application/json".toMediaTypeOrNull(), jsonRequest.toString())

            val request = Request.Builder()
                .url(GPT_API_URL)
                .addHeader("Authorization", "Bearer $GPT_API_KEY")
                .post(requestBody)
                .build()

            try {
                val response = httpClient.newCall(request).execute()
                val jsonResponse = JSONObject(response.body?.string() ?: "")
                val gptResponse = jsonResponse.getJSONArray("choices").getJSONObject(0).getString("text")  // Corrected line for extracting the response text
                Log.i("GPT_RESPONSE", gptResponse)  // Log the response

                withContext(Dispatchers.Main) {
                    tts.speak(gptResponse, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            } catch (e: Exception) {
                Log.e("GPT_API", "Error communicating with API", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::porcupineManager.isInitialized) {
            porcupineManager.start()
        }
    }

    override fun onPause() {
        if (::porcupineManager.isInitialized) {
            porcupineManager.stop()
        }
        super.onPause()
    }
    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.shutdown()
        }
        super.onDestroy()
    }
}
