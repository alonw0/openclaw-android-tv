package ai.openclaw.tv.gateway

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.SecureRandom
import java.security.Security
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.jce.provider.BouncyCastleProvider

@Serializable
data class DeviceIdentity(
  val deviceId: String,
  val publicKeyRawBase64: String,
  val privateKeyRawBase64: String,
  val createdAtMs: Long,
)

class DeviceIdentityStore(context: Context) {
  private val json = Json { ignoreUnknownKeys = true }
  private val identityFile = File(context.filesDir, "openclaw/identity/device.json")

  init {
    // Register Bouncy Castle provider if not already registered
    if (Security.getProvider("BC") == null) {
      Security.addProvider(BouncyCastleProvider())
    }
  }

  @Synchronized
  fun loadOrCreate(): DeviceIdentity {
    val existing = load()
    if (existing != null) {
      val derived = deriveDeviceId(existing.publicKeyRawBase64)
      if (derived != null && derived != existing.deviceId) {
        val updated = existing.copy(deviceId = derived)
        save(updated)
        return updated
      }
      return existing
    }
    
    // Generate fresh identity using Bouncy Castle
    val fresh = generate()
    save(fresh)
    return fresh
  }

  fun signPayload(payload: String, identity: DeviceIdentity): String? {
    return try {
      val privateKeyBytes = Base64.decode(identity.privateKeyRawBase64, Base64.DEFAULT)
      Log.d("DeviceIdentityStore", "Signing with ${privateKeyBytes.size} byte private key")
      
      // Use Bouncy Castle for Ed25519 signing
      val privateKeyParams = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
      val signer = Ed25519Signer()
      signer.init(true, privateKeyParams)
      signer.update(payload.toByteArray(Charsets.UTF_8), 0, payload.length)
      val signatureBytes = signer.generateSignature()
      
      val result = base64UrlEncode(signatureBytes)
      Log.d("DeviceIdentityStore", "Signature generated: ${result.take(20)}...")
      result
    } catch (e: Throwable) {
      Log.e("DeviceIdentityStore", "Failed to sign payload: ${e.message}", e)
      null
    }
  }

  fun publicKeyBase64Url(identity: DeviceIdentity): String? {
    return try {
      val raw = Base64.decode(identity.publicKeyRawBase64, Base64.DEFAULT)
      base64UrlEncode(raw)
    } catch (_: Throwable) {
      null
    }
  }

  private fun load(): DeviceIdentity? {
    return readIdentity(identityFile)
  }

  private fun readIdentity(file: File): DeviceIdentity? {
    return try {
      if (!file.exists()) return null
      val raw = file.readText(Charsets.UTF_8)
      val decoded = json.decodeFromString(DeviceIdentity.serializer(), raw)
      if (decoded.deviceId.isBlank() ||
        decoded.publicKeyRawBase64.isBlank() ||
        decoded.privateKeyRawBase64.isBlank()
      ) {
        null
      } else {
        decoded
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun save(identity: DeviceIdentity) {
    try {
      // Ensure parent directory exists
      identityFile.absolutePath.substringBeforeLast('/').takeIf { it.isNotEmpty() }?.let {
        File(it).mkdirs()
      }
      val encoded = json.encodeToString(DeviceIdentity.serializer(), identity)
      identityFile.writeText(encoded, Charsets.UTF_8)
    } catch (_: Throwable) {
      // best-effort only
    }
  }

  /**
   * Generate Ed25519 keypair using Bouncy Castle.
   * This works reliably on both emulators and real devices.
   */
  private fun generate(): DeviceIdentity {
    Log.i("DeviceIdentityStore", "Generating Ed25519 keypair with Bouncy Castle")
    
    // Generate using Bouncy Castle's Ed25519
    val secureRandom = SecureRandom()
    val privateKeyParams = Ed25519PrivateKeyParameters(secureRandom)
    val publicKeyParams = privateKeyParams.generatePublicKey()
    
    // Get raw key bytes (32 bytes each for Ed25519)
    val privateKeyBytes = privateKeyParams.encoded
    val publicKeyBytes = publicKeyParams.encoded
    
    Log.d("DeviceIdentityStore", "Generated keys: private=${privateKeyBytes.size} bytes, public=${publicKeyBytes.size} bytes")
    
    // Device ID is SHA-256 of public key
    val deviceId = sha256Hex(publicKeyBytes)
    
    return DeviceIdentity(
      deviceId = deviceId,
      publicKeyRawBase64 = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP),
      privateKeyRawBase64 = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP),
      createdAtMs = System.currentTimeMillis(),
    )
  }

  private fun deriveDeviceId(publicKeyRawBase64: String): String? {
    return try {
      val raw = Base64.decode(publicKeyRawBase64, Base64.DEFAULT)
      sha256Hex(raw)
    } catch (_: Throwable) {
      null
    }
  }

  private fun sha256Hex(data: ByteArray): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(data)
    val out = StringBuilder(digest.size * 2)
    for (byte in digest) {
      out.append(String.format("%02x", byte))
    }
    return out.toString()
  }

  private fun base64UrlEncode(data: ByteArray): String {
    return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }
}
