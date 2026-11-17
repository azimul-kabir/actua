package com.azimulkabir.actua.billing

import android.app.Activity
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.azimulkabir.actua.BuildConfig
import com.azimulkabir.actua.billing.website.SandboxEntitlementVerifier
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Sandbox-only state is encrypted and excluded from Android backup and device transfer. */
private class SandboxPurchaseStore(private val context: Context) {
    private val file get() = java.io.File(context.noBackupFilesDir, "supporter-sandbox")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("actua.supporter.sandbox", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("actua.supporter.sandbox", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): JSONObject? = try {
        val bytes = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    } catch (_: Exception) { null }
    @Synchronized fun write(value: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val atomic = android.util.AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(cipher.iv + cipher.doFinal(value.toString().toByteArray(Charsets.UTF_8)))
            atomic.finishWrite(output)
        } catch (error: Exception) { atomic.failWrite(output); throw error }
    }
    @Synchronized fun clear() { file.delete() }
}

private object SandboxPurchases {
    val state = MutableStateFlow(SupportPurchaseState(websiteAvailable = BuildConfig.DEBUG))
    private val verifier = SandboxEntitlementVerifier()
    private val client = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    @Volatile private var validUntil = 0L
    @Volatile private var checkedAt = Long.MAX_VALUE
    @Volatile private var elapsedUntil = 0L
    fun valid(context: Context): Boolean {
        val now = System.currentTimeMillis() / 1000
        return BuildConfig.DEBUG && now >= checkedAt && now < validUntil &&
            android.os.SystemClock.elapsedRealtime() < elapsedUntil
    }
    private fun loadValidity(store: SandboxPurchaseStore): Boolean {
        validUntil = 0
        elapsedUntil = 0
        val stored = store.read() ?: return false
        val now = System.currentTimeMillis() / 1000
        return try {
            checkedAt = stored.getLong("checked")
            if (now < checkedAt) return false
            validUntil = verifier.verify(stored.getString("token"), now)
            elapsedUntil = android.os.SystemClock.elapsedRealtime() + (validUntil - now) * 1000
            true
        } catch (_: Exception) { false }
    }
    suspend fun refresh(context: Context, entered: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (!BuildConfig.DEBUG) return@withContext false
        val store = SandboxPurchaseStore(context)
        val code = entered ?: store.read()?.optString("credential")
        if (code == null || !Regex("[A-Za-z0-9_-]{43}").matches(code)) return@withContext false
        val request = Request.Builder().url("https://actua-sandbox.azimulkabir.com/api/supporter/entitlement")
            .header("Authorization", "Bearer $code")
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        try {
            client.newCall(request).execute().use { response ->
                if ((response.code == 401 || response.code == 403) &&
                    response.header("Content-Type")?.startsWith("application/json") == true) {
                    if (entered == null) store.clear()
                    return@use false
                }
                check(response.isSuccessful)
                val token = JSONObject(response.body!!.string()).getString("entitlement")
                val now = System.currentTimeMillis() / 1000
                verifier.verify(token, now)
                store.write(JSONObject().put("credential", code).put("token", token).put("checked", now))
                true
            }
        } catch (error: CancellationException) { throw error } catch (_: Exception) { false }
        finally {
            val owned = loadValidity(store)
            state.value = SupportPurchaseState(websiteAvailable = true, websiteSupporter = owned, purchasesRestored = true)
            withContext(Dispatchers.Main) { com.azimulkabir.actua.data.preferences.SupporterPerks(context).record(owned) }
        }
    }
}

private class DirectSupportBillingManager(private val context: Context) : SupportBillingManager {
    private var scope: CoroutineScope? = null
    override val state: StateFlow<SupportPurchaseState> = SandboxPurchases.state
    override fun start() {
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { it.launch { SandboxPurchases.refresh(context) } }
    }
    override fun stop() { scope?.cancel(); scope = null }
    override fun launchPurchase(activity: Activity, product: SupportProduct) = Unit
    override fun dismissThanks() = Unit
}

fun createSupportBillingManager(context: Context): SupportBillingManager = DirectSupportBillingManager(context.applicationContext)
fun supporterPerksAvailable(): Boolean = true
fun websiteEntitlementValid(context: Context): Boolean = SandboxPurchases.valid(context)

@Composable
fun WebsiteActivation() {
    if (!BuildConfig.DEBUG) { Text("Website purchases are not available yet."); return }
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Enter your sandbox purchase recovery code. This does not make a payment.") }
    Column {
        Text(message)
        OutlinedTextField(value = code, onValueChange = { code = it }, enabled = !busy,
            label = { Text("Sandbox purchase recovery code") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password))
        Button(enabled = !busy && code.length == 43, onClick = {
            val submitted = code
            code = ""
            busy = true
            scope.launch {
                try {
                    message = if (SandboxPurchases.refresh(context, submitted)) "Sandbox purchase verified." else "Could not verify this purchase. Check your code and connection."
                } finally { busy = false }
            }
        }) { Text(if (busy) "Verifying…" else "Verify sandbox purchase") }
    }
}

fun supporterRefreshDue(): Boolean = BuildConfig.DEBUG

fun supporterPreferencesName(): String = "supporter_perks_direct"
