package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class VpnRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun getFirebaseIdToken(): String? {
        return try {
            auth.currentUser?.getIdToken(false)?.await()?.token
        } catch (_: Exception) {
            null
        }
    }

    suspend fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.isEmailVerified && user.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true)) {
            return true
        }
        return try {
            val doc = db.collection("admins").document(user.uid).get().await()
            doc.exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admins/${user.uid}")
            false
        }
    }

    suspend fun ensureUserProfile(user: FirebaseUser, clientPublicKey: String): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val existingSnap = docRef.get().await()
            val safeName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")
                ?: "Fast X User"
            val safeEmail = user.email?.takeIf { it.isNotBlank() } ?: "user@fastxvpn.app"
            val photoUrl = user.photoUrl?.toString()?.take(500)

            if (!existingSnap.exists()) {
                val createMap = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to safeName.take(100),
                    "email" to safeEmail.take(150),
                    "clientPublicKey" to clientPublicKey.take(128),
                    "totalBytesDown" to 0L,
                    "totalBytesUp" to 0L,
                    "totalSessions" to 0L,
                    "totalConnectedSeconds" to 0L,
                    "autoReconnect" to true,
                    "plan" to "FREE",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!photoUrl.isNullOrBlank()) {
                    createMap["photoUrl"] = photoUrl
                }
                docRef.set(createMap).await()
            } else {
                val updateMap = mutableMapOf<String, Any>(
                    "displayName" to safeName.take(100),
                    "email" to safeEmail.take(150),
                    "clientPublicKey" to clientPublicKey.take(128),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!photoUrl.isNullOrBlank()) {
                    updateMap["photoUrl"] = photoUrl
                }
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    logStreamError(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    fun observeActiveServers(): Flow<List<VpnServer>> = flow {
        val uid = requireUserId()
        val path = "vpn_servers"
        emitAll(
            db.collection(path)
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VpnServer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.country }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeAllServersForAdmin(): Flow<List<VpnServer>> = flow {
        val uid = requireUserId()
        val path = "vpn_servers"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(VpnServer::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.country }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.totalSessions }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, uid)
                    throw error
                }
        )
    }

    fun observeSessions(userId: String): Flow<List<VpnSession>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .snapshots()
                .map { snap ->
                    snap.toObjects(VpnSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeCustomConfigs(userId: String): Flow<List<CustomWireGuardConfig>> = flow {
        require(userId.isNotBlank())
        val path = "users/$userId/custom_configs"
        emitAll(
            db.collection("users").document(userId).collection("custom_configs")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CustomWireGuardConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name }
                }
                .catch { error ->
                    logStreamError(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun recordSession(userId: String, summary: SessionSummary): Result<Unit> {
        val sessionId = "sess_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val sessionPath = "users/$userId/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }

            val sessionPayload = mapOf(
                "userId" to uid,
                "serverId" to summary.server.id.ifBlank { "custom" }.take(128),
                "serverName" to summary.server.name.ifBlank { "WireGuard Node" }.take(80),
                "country" to summary.server.country.ifBlank { "Custom" }.take(60),
                "protocol" to "WireGuard",
                "bytesDown" to summary.bytesDown.coerceAtLeast(0L),
                "bytesUp" to summary.bytesUp.coerceAtLeast(0L),
                "durationSeconds" to summary.durationSeconds.coerceAtLeast(0L),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
                .set(sessionPayload)
                .await()

            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "totalBytesDown" to FieldValue.increment(summary.bytesDown.coerceAtLeast(0L)),
                        "totalBytesUp" to FieldValue.increment(summary.bytesUp.coerceAtLeast(0L)),
                        "totalSessions" to FieldValue.increment(1L),
                        "totalConnectedSeconds" to FieldValue.increment(summary.durationSeconds.coerceAtLeast(0L)),
                        "lastServerId" to summary.server.id.take(128),
                        "lastServerName" to summary.server.name.take(80),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, sessionPath)
            Result.failure(e)
        }
    }

    suspend fun setAutoReconnect(userId: String, enabled: Boolean): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            db.collection("users").document(uid).update(
                mapOf(
                    "autoReconnect" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveCustomConfig(userId: String, config: CustomWireGuardConfig): Result<String> {
        val docId = config.id.ifBlank {
            "cfg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        }
        val path = "users/$userId/custom_configs/$docId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user write." }
            require(config.name.trim().length in 1..80) { "Config name must be 1–80 characters." }
            require(config.endpointHost.trim().length in 3..253) { "Endpoint host is required." }
            require(config.endpointPort in 1L..65535L) { "Endpoint port must be 1–65535." }
            require(config.serverPublicKey.trim().length in 32..128) { "Valid WireGuard public key is required." }

            val docRef = db.collection("users").document(uid)
                .collection("custom_configs").document(docId)
            val isNew = config.id.isBlank()

            if (isNew) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to config.name.trim().take(80),
                        "endpointHost" to config.endpointHost.trim().take(253),
                        "endpointPort" to config.endpointPort.coerceIn(1L, 65535L),
                        "serverPublicKey" to config.serverPublicKey.trim().take(128),
                        "clientAddressCidr" to config.clientAddressCidr.trim().ifBlank { "10.66.66.2/32" }.take(64),
                        "dns" to config.dns.trim().ifBlank { "1.1.1.1, 1.0.0.1" }.take(120),
                        "allowedIps" to config.allowedIps.trim().ifBlank { "0.0.0.0/0, ::/0" }.take(120),
                        "mtu" to config.mtu.coerceIn(1200L, 1500L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to config.name.trim().take(80),
                        "endpointHost" to config.endpointHost.trim().take(253),
                        "endpointPort" to config.endpointPort.coerceIn(1L, 65535L),
                        "serverPublicKey" to config.serverPublicKey.trim().take(128),
                        "clientAddressCidr" to config.clientAddressCidr.trim().ifBlank { "10.66.66.2/32" }.take(64),
                        "dns" to config.dns.trim().ifBlank { "1.1.1.1, 1.0.0.1" }.take(120),
                        "allowedIps" to config.allowedIps.trim().ifBlank { "0.0.0.0/0, ::/0" }.take(120),
                        "mtu" to config.mtu.coerceIn(1200L, 1500L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomConfig(userId: String, configId: String): Result<Unit> {
        val path = "users/$userId/custom_configs/$configId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            db.collection("users").document(uid)
                .collection("custom_configs").document(configId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Admin Operations ---

    suspend fun saveAdminServer(server: VpnServer, isNew: Boolean): Result<Unit> {
        val serverId = server.id.ifBlank {
            "${server.countryCode.lowercase().ifBlank { "node" }}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        }.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val path = "vpn_servers/$serverId"
        return try {
            requireUserId()
            require(server.name.trim().length in 1..80) { "Server name must be 1–80 chars." }
            require(server.endpointHost.trim().length in 3..253) { "Endpoint host is required." }
            require(server.endpointPort in 1L..65535L) { "Port must be 1–65535." }
            val docRef = db.collection("vpn_servers").document(serverId)
            if (isNew) {
                docRef.set(server.toCreateMap()).await()
            } else {
                docRef.update(server.toUpdateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleServerActive(serverId: String, active: Boolean): Result<Unit> {
        val path = "vpn_servers/$serverId"
        return try {
            requireUserId()
            db.collection("vpn_servers").document(serverId)
                .update(
                    mapOf(
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteServer(serverId: String): Result<Unit> {
        val path = "vpn_servers/$serverId"
        return try {
            requireUserId()
            db.collection("vpn_servers").document(serverId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultRegionNodes(): Result<Unit> {
        return try {
            requireUserId()
            val existing = db.collection("vpn_servers").get().await()
            val existingIds = existing.documents.map { it.id }.toSet()
            for (template in DefaultRegionTemplates.sixCoreRegions) {
                if (template.id !in existingIds) {
                    db.collection("vpn_servers").document(template.id)
                        .set(template.toCreateMap())
                        .await()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "vpn_servers")
            Result.failure(e)
        }
    }

    private fun logStreamError(error: Throwable, op: OperationType, path: String, expectedUid: String) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val ex = firestoreError ?: (error as? Exception)
        if (ex != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(ex, op, path)
        }
    }

    companion object {
        const val BOOTSTRAP_ADMIN_EMAIL = "ltlamiya146@gmail.com"
    }
}
