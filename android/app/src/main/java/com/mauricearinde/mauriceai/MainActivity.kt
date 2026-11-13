package com.mauricearinde.mauriceai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.util.Locale
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

class MainActivity : Activity() {
    private lateinit var db: LocalStore
    private lateinit var chat: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private val worker = Executors.newSingleThreadExecutor()
    private val navy = Color.rgb(7, 29, 56)
    private val blue = Color.rgb(25, 189, 242)
    private val ink = Color.rgb(27, 43, 61)
    private val speechRequest = 81

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LocalStore(this)
        buildScreen()
        showSavedConversation()
        updateStatus()
    }

    private fun buildScreen() {
        val root = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 248, 252))
        }
        val header = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
            setBackgroundColor(navy)
        }
        val titleRow = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this@MainActivity).apply {
            text = "MAURICE AI"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        val settings = Button(this@MainActivity).apply {
            text = "SETTINGS"
            textSize = 11f
            setTextColor(Color.WHITE)
            background = rounded(blue, 18)
            setOnClickListener { settingsDialog() }
        }
        titleRow.addView(brand, LinearLayout.LayoutParams(0, dp(42), 1f))
        titleRow.addView(settings)
        status = TextView(this@MainActivity).apply {
            textSize = 12f
            setTextColor(Color.rgb(174, 218, 239))
            text = "Checking connection..."
            setPadding(0, dp(5), 0, 0)
        }
        header.addView(titleRow)
        header.addView(status)
        root.addView(header)

        scroll = ScrollView(this@MainActivity).apply { isFillViewport = true }
        chat = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            setBackgroundColor(Color.WHITE)
        }
        input = EditText(this@MainActivity).apply {
            hint = "Message MAURICE AI..."
            textSize = 15f
            minLines = 1
            maxLines = 4
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(Color.rgb(241, 246, 250), 16)
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
            }
        }
        val actions = LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val voice = Button(this@MainActivity).apply {
            text = "VOICE"
            textSize = 11f
            setOnClickListener { startVoiceInput() }
        }
        val memory = Button(this@MainActivity).apply {
            text = "MEMORY"
            textSize = 11f
            setOnClickListener { showMemory() }
        }
        val send = Button(this@MainActivity).apply {
            text = "SEND"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = rounded(navy, 18)
            setOnClickListener { sendMessage() }
        }
        actions.addView(voice)
        actions.addView(memory)
        actions.addView(View(this@MainActivity), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(send)
        composer.addView(input)
        composer.addView(actions)
        root.addView(composer)
        setContentView(root)
        addBubble("MAURICE AI", "I'm ready. I work with Gemini when you're online, and keep your saved memory and basic commands available offline.\n\nTry: help, remember I prefer short answers, show memory, or ask me a question.", false)
    }

    private fun sendMessage() {
        val message = input.text.toString().trim()
        if (message.isEmpty()) return
        input.setText("")
        hideKeyboard()
        db.addMessage("user", message)
        addBubble("YOU", message, true)
        val local = handleLocalCommand(message)
        if (local != null) {
            db.addMessage("model", local)
            addBubble("MAURICE AI · OFFLINE", local, false)
            return
        }
        val key = SecureKeyStore.get(this)
        if (key.isNullOrBlank()) {
            val reply = "You're offline-ready, but Gemini hasn't been connected yet. Open SETTINGS and add your free Gemini API key. Your message is saved on this phone. Type 'help' to see offline commands."
            db.addMessage("model", reply)
            addBubble("MAURICE AI · LOCAL", reply, false)
            return
        }
        if (!hasInternet()) {
            val reply = "You're offline. Your message is saved on this phone, and your saved memory is still available. Gemini answers will resume when internet returns. Type 'help' for offline commands."
            db.addMessage("model", reply)
            addBubble("MAURICE AI · OFFLINE", reply, false)
            updateStatus()
            return
        }
        val thinking = addBubble("MAURICE AI", "Thinking...", false)
        worker.execute {
            val result = try { GeminiClient.ask(key, db.recentMessages(12), db.getNotes()) }
            catch (e: Exception) { "I couldn't reach Gemini. Your conversation is saved locally. Check your connection and API key, then try again.\n\nDetails: ${e.message ?: "network error"}" }
            runOnUiThread {
                db.addMessage("model", result)
                chat.removeView(thinking)
                addBubble("MAURICE AI · GEMINI", result, false)
                updateStatus()
            }
        }
        updateStatus()
    }

    private fun handleLocalCommand(raw: String): String? {
        val msg = raw.trim()
        val lower = msg.lowercase(Locale.ROOT)
        return when {
            lower == "help" || lower == "/help" -> "OFFLINE COMMANDS\n\n• remember [text] — save something to memory\n• note [text] — save a note\n• show memory — list saved notes\n• time — show the current time\n• date — show today's date\n• clear history — erase conversation history\n\nNormal questions use Gemini when internet is available."
            lower.startsWith("remember ") -> {
                val value = msg.substringAfter(' ').trim()
                if (value.isBlank()) "Tell me what to remember." else { db.addNote(value); "Saved to your on-device memory. It will be available offline." }
            }
            lower.startsWith("note ") -> {
                val value = msg.substringAfter(' ').trim()
                if (value.isBlank()) "Write something after 'note'." else { db.addNote(value); "Note saved on this phone." }
            }
            lower == "show memory" || lower == "show notes" || lower == "memory" -> {
                val notes = db.getNotes()
                if (notes.isEmpty()) "Your memory is empty. Use 'remember [something]' to save your first memory." else "YOUR SAVED MEMORY\n\n" + notes.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("\n\n")
            }
            lower == "time" || lower == "what time is it" -> "The time is ${java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(java.util.Date())}."
            lower == "date" || lower == "what is today's date" || lower == "what's today's date" -> "Today is ${java.text.SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(java.util.Date())}."
            lower == "clear history" -> { db.clearMessages(); chat.removeAllViews(); "Conversation history cleared. Your saved memory notes were kept." }
            else -> null
        }
    }

    private fun showSavedConversation() {
        val messages = db.recentMessages(60)
        if (messages.isEmpty()) return
        chat.removeAllViews()
        messages.forEach { (role, text) -> addBubble(if (role == "user") "YOU" else "MAURICE AI", text, role == "user") }
    }

    private fun addBubble(label: String, message: String, user: Boolean): View {
        val wrapper = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (user) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }
        val bubble = TextView(this@MainActivity).apply {
            text = "$label\n$message"
            textSize = 14f
            setTextColor(if (user) Color.WHITE else ink)
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = rounded(if (user) navy else Color.WHITE, 16)
            maxWidth = resources.displayMetrics.widthPixels * 85 / 100
        }
        wrapper.addView(bubble)
        chat.addView(wrapper)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return wrapper
    }

    private fun settingsDialog() {
        val box = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val explanation = TextView(this@MainActivity).apply {
            text = "Gemini API key\nGet a key free from Google AI Studio. The key is encrypted using Android Keystore and is not included in the APK or GitHub repository. Gemini usage is subject to Google's free-tier limits."
            textSize = 13f
            setTextColor(ink)
        }
        val keyInput = EditText(this@MainActivity).apply {
            hint = "Paste Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(SecureKeyStore.get(this@MainActivity) ?: "")
        }
        box.addView(explanation)
        box.addView(keyInput)
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(box)
            .setPositiveButton("SAVE KEY") { _, _ ->
                val value = keyInput.text.toString().trim()
                if (value.isNotEmpty()) SecureKeyStore.put(this, value)
                updateStatus()
                Toast.makeText(this, if (value.isEmpty()) "No changes made" else "Gemini key saved securely", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("REMOVE KEY") { _, _ -> SecureKeyStore.delete(this); updateStatus(); Toast.makeText(this, "Gemini key removed", Toast.LENGTH_SHORT).show() }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showMemory() {
        val notes = db.getNotes()
        AlertDialog.Builder(this)
            .setTitle("On-device memory")
            .setMessage(if (notes.isEmpty()) "No saved memories yet. Type: remember [text]" else notes.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n\n"))
            .setPositiveButton("OK", null)
            .setNeutralButton("CLEAR MEMORY") { _, _ -> db.clearNotes(); Toast.makeText(this, "Saved memory cleared", Toast.LENGTH_SHORT).show() }
            .show()
    }

    private fun startVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to MAURICE AI")
            }
            startActivityForResult(intent, speechRequest)
        } catch (_: Exception) {
            Toast.makeText(this, "Speech recognition is not available on this device.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Legacy speech activity result for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == speechRequest && resultCode == RESULT_OK) {
            val words = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!words.isNullOrBlank()) { input.setText(words); input.setSelection(input.text.length) }
        }
    }

    private fun hasInternet(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork
        val capabilities = network?.let { manager.getNetworkCapabilities(it) }
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun updateStatus() {
        val connected = hasInternet()
        val hasKey = !SecureKeyStore.get(this).isNullOrBlank()
        status.text = when { connected && hasKey -> "● ONLINE · GEMINI READY"; connected -> "● ONLINE · ADD GEMINI KEY IN SETTINGS"; else -> "● OFFLINE · LOCAL MEMORY ACTIVE" }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}

private class LocalStore(context: Context) : android.database.sqlite.SQLiteOpenHelper(context, "maurice_ai.db", null, 1) {
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE notes(id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun addMessage(role: String, body: String) { writableDatabase.execSQL("INSERT INTO messages(role,body,created_at) VALUES(?,?,?)", arrayOf(role, body, System.currentTimeMillis())) }
    fun addNote(body: String) { writableDatabase.execSQL("INSERT INTO notes(body,created_at) VALUES(?,?)", arrayOf(body, System.currentTimeMillis())) }
    fun getNotes(): List<String> {
        val result = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT body FROM notes ORDER BY id DESC LIMIT 40", null).use { c -> while (c.moveToNext()) result.add(c.getString(0)) }
        return result.reversed()
    }
    fun recentMessages(limit: Int): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        readableDatabase.rawQuery("SELECT role,body FROM messages ORDER BY id DESC LIMIT $limit", null).use { c -> while (c.moveToNext()) result.add(c.getString(0) to c.getString(1)) }
        return result.reversed()
    }
    fun clearMessages() { writableDatabase.delete("messages", null, null) }
    fun clearNotes() { writableDatabase.delete("notes", null, null) }
}

private object GeminiClient {
    private const val MODEL = "gemini-3.8-flash"
    fun ask(apiKey: String, history: List<Pair<String, String>>, notes: List<String>): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=${URLEncoder.encode(apiKey, "UTF-8")}")
        val contents = JSONArray()
        history.takeLast(12).forEach { (role, body) ->
            contents.put(JSONObject().put("role", if (role == "model") "model" else "user").put("parts", JSONArray().put(JSONObject().put("text", body.take(6000)))))
        }
        val memory = if (notes.isEmpty()) "No saved user memory." else notes.joinToString("\n") { "- $it" }
        val payload = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "You are MAURICE AI, a concise, helpful personal Android assistant created by Maurice Arinde. Use the saved memory only when relevant. Be honest about uncertainty. Do not claim to perform phone actions you cannot perform. Saved user memory:\n$memory"))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 900))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val message = try { JSONObject(response).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                throw IllegalStateException(message ?: "Gemini API returned HTTP $code")
            }
            val candidates = JSONObject(response).optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val answer = parts?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("text") }.joinToString("\n") }?.trim()
            return answer?.takeIf { it.isNotEmpty() } ?: "Gemini returned an empty response. Try again."
        } finally { connection.disconnect() }
    }
}

private object SecureKeyStore {
    private const val ALIAS = "maurice-ai-gemini-key"
    private const val PREFS = "secure_settings"
    fun put(context: Context, value: String) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }
    fun get(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ivText = prefs.getString("iv", null) ?: return null
        val dataText = prefs.getString("data", null) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
    fun delete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        try { KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) } } catch (_: Exception) {}
    }
    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val spec = android.security.keystore.KeyGenParameterSpec.Builder(ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}
