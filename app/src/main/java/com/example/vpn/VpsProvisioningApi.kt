package com.example.vpn

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

@JsonClass(generateAdapter = true)
data class PeerRegisterRequest(
    val clientPublicKey: String,
    val userId: String,
    val serverId: String
)

@JsonClass(generateAdapter = true)
data class PeerRegisterResponse(
    val clientAddressCidr: String,
    val serverPublicKey: String,
    val endpointHost: String? = null,
    val endpointPort: Long? = null,
    val dns: String? = null,
    val allowedIps: String? = null,
    val mtu: Long? = null,
    val persistentKeepalive: Long? = null
)

@JsonClass(generateAdapter = true)
data class VpsHealthResponse(
    val status: String,
    val region: String? = null,
    val activePeers: Int = 0,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val loadPercent: Int = 0,
    val uptimeSeconds: Long = 0L,
    val interfaceName: String = "wg0"
)

interface VpsProvisioningService {
    @POST
    suspend fun registerPeer(
        @Url fullUrl: String,
        @Header("Authorization") bearerToken: String,
        @Body request: PeerRegisterRequest
    ): PeerRegisterResponse

    @GET
    suspend fun getHealth(
        @Url fullUrl: String
    ): VpsHealthResponse
}

class VpsProvisioningClient {
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://localhost/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(VpsProvisioningService::class.java)

    suspend fun registerPeer(
        provisionBaseUrl: String,
        firebaseIdToken: String,
        clientPublicKey: String,
        userId: String,
        serverId: String
    ): PeerRegisterResponse = withContext(Dispatchers.IO) {
        val base = provisionBaseUrl.trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) {
            "Provisioning API URL must start with https:// or http://"
        }
        api.registerPeer(
            fullUrl = "$base/api/v1/peers/register",
            bearerToken = "Bearer $firebaseIdToken",
            request = PeerRegisterRequest(
                clientPublicKey = clientPublicKey,
                userId = userId,
                serverId = serverId
            )
        )
    }

    suspend fun checkHealth(provisionBaseUrl: String): Result<VpsHealthResponse> =
        withContext(Dispatchers.IO) {
            try {
                val base = provisionBaseUrl.trim().trimEnd('/')
                require(base.startsWith("https://") || base.startsWith("http://")) {
                    "Invalid VPS API URL"
                }
                Result.success(api.getHealth("$base/health"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun measureEndpointLatencyMs(host: String, port: Int): Long =
        withContext(Dispatchers.IO) {
            if (host.isBlank()) return@withContext -1L
            // Check TCP reachability on provisioning port or standard 443/22/endpointPort fallback
            val portsToTry = listOf(port, 443, 8443, 80).distinct()
            for (candidatePort in portsToTry) {
                try {
                    var elapsed = 0L
                    Socket().use { socket ->
                        elapsed = measureTimeMillis {
                            socket.connect(InetSocketAddress(host, candidatePort), 1800)
                        }
                    }
                    return@withContext elapsed.coerceAtLeast(1L)
                } catch (_: Exception) {
                    // Try next port
                }
            }
            -1L
        }
}
