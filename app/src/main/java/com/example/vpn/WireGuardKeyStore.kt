package com.example.vpn

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wireguard.crypto.Key
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vpnDataStore: DataStore<Preferences> by preferencesDataStore(name = "fastx_vpn_prefs")

class WireGuardKeyStore(context: Context) {
    private val dataStore = context.applicationContext.vpnDataStore

    private object Keys {
        val DEVICE_PRIVATE_KEY = stringPreferencesKey("wg_device_private_key")
        val DEVICE_PUBLIC_KEY = stringPreferencesKey("wg_device_public_key")
        val SELECTED_SERVER_ID = stringPreferencesKey("selected_server_id")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect_enabled")
        fun customPrivateKey(configId: String) = stringPreferencesKey("custom_pk_$configId")
    }

    suspend fun getOrCreateDeviceKeyPair(): KeyPair {
        val prefs = dataStore.data.first()
        val storedPriv = prefs[Keys.DEVICE_PRIVATE_KEY]
        if (!storedPriv.isNullOrBlank()) {
            try {
                return KeyPair(Key.fromBase64(storedPriv))
            } catch (_: Exception) {
                // Regenerate if corrupted
            }
        }
        val fresh = KeyPair()
        dataStore.edit { mutable ->
            mutable[Keys.DEVICE_PRIVATE_KEY] = fresh.privateKey.toBase64()
            mutable[Keys.DEVICE_PUBLIC_KEY] = fresh.publicKey.toBase64()
        }
        return fresh
    }

    suspend fun getDevicePublicKey(): String = getOrCreateDeviceKeyPair().publicKey.toBase64()

    val selectedServerIdFlow: Flow<String?> = dataStore.data.map { it[Keys.SELECTED_SERVER_ID] }

    suspend fun setSelectedServerId(serverId: String) {
        dataStore.edit { it[Keys.SELECTED_SERVER_ID] = serverId }
    }

    val autoReconnectFlow: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_RECONNECT] ?: true }

    suspend fun setAutoReconnect(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_RECONNECT] = enabled }
    }

    suspend fun saveCustomPrivateKey(configId: String, privateKeyBase64: String) {
        dataStore.edit { it[Keys.customPrivateKey(configId)] = privateKeyBase64.trim() }
    }

    suspend fun getCustomPrivateKey(configId: String): String? {
        return dataStore.data.first()[Keys.customPrivateKey(configId)]
    }

    suspend fun removeCustomPrivateKey(configId: String) {
        dataStore.edit { it.remove(Keys.customPrivateKey(configId)) }
    }
}
