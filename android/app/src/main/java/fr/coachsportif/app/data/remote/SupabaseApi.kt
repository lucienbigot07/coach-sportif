package fr.coachsportif.app.data.remote

import fr.coachsportif.app.data.auth.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

/** Erreur métier renvoyée par une fonction SQL (PostgREST : PTnnn → HTTP nnn). */
class ApiException(val status: Int, val code: String, val detail: String?) : Exception("$status $code")

/** Réseau indisponible : l'opération reste dans la file d'attente. */
class OfflineException(cause: Throwable) : IOException(cause)

/**
 * Client minimal Supabase : Auth (mot de passe / refresh) et RPC PostgREST.
 * Seule la clé PUBLIQUE est embarquée ; chaque requête porte le JWT de l'utilisateur → RLS.
 */
class SupabaseApi(private val baseUrl: String, private val anonKey: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    val isConfigured: Boolean get() = baseUrl.startsWith("https://") && anonKey.isNotBlank()

    suspend fun signIn(email: String, password: String): Session =
        token("password", buildJsonObject { put("email", email); put("password", password) })

    suspend fun refresh(refreshToken: String): Session =
        token("refresh_token", buildJsonObject { put("refresh_token", refreshToken) })

    private suspend fun token(grant: String, body: JsonObject): Session {
        val res = post("$baseUrl/auth/v1/token?grant_type=$grant", body.toString(), null)
        val o = Json.parseToJsonElement(res).jsonObject
        val user = o["user"]!!.jsonObject
        return Session(
            userId = user["id"]!!.jsonPrimitive.content,
            email = user["email"]?.jsonPrimitive?.content,
            accessToken = o["access_token"]!!.jsonPrimitive.content,
            refreshToken = o["refresh_token"]!!.jsonPrimitive.content,
            expiresAtEpochS = o["expires_at"]?.jsonPrimitive?.long
                ?: (System.currentTimeMillis() / 1000 + (o["expires_in"]?.jsonPrimitive?.long ?: 3600)),
        )
    }

    /** POST /rest/v1/rpc/<fn> avec des arguments nommés (p_…). */
    suspend fun rpc(fn: String, args: JsonObject, accessToken: String): JsonElement {
        require(Regex("^[a-z_][a-z0-9_]*$").matches(fn))
        val res = post("$baseUrl/rest/v1/rpc/$fn", args.toString(), accessToken)
        return Json.parseToJsonElement(res)
    }

    /** Lecture directe d'une table (SELECT autorisé par RLS), ex. liste des variantes. */
    suspend fun select(path: String, accessToken: String): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/rest/v1/$path")
            .header("apikey", anonKey).header("Authorization", "Bearer $accessToken").get().build()
        execute(req)
    }.let { Json.parseToJsonElement(it) }

    private suspend fun post(url: String, body: String, accessToken: String?): String = withContext(Dispatchers.IO) {
        val b = Request.Builder().url(url).header("apikey", anonKey)
            .post(body.toRequestBody(jsonType))
        if (accessToken != null) b.header("Authorization", "Bearer $accessToken")
        execute(b.build())
    }

    private fun execute(req: Request): String {
        val resp = try {
            http.newCall(req).execute()
        } catch (e: IOException) {
            throw OfflineException(e)
        }
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                val code = o?.get("message")?.jsonPrimitive?.content
                    ?: o?.get("error_code")?.jsonPrimitive?.content
                    ?: o?.get("error")?.jsonPrimitive?.content ?: "http_${it.code}"
                throw ApiException(it.code, code, (o?.get("details") as? JsonPrimitive)?.contentOrNull)
            }
            return text
        }
    }
}
