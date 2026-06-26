package com.example.data.repository

import com.example.data.model.ShareToken
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed class JoinResult {
    object NotFound : JoinResult()
    object Expired : JoinResult()
    object AlreadyUsed : JoinResult()
    object LimitReached : JoinResult()
    data class Success(val hostUid: String, val bwLimitMB: Long) : JoinResult()
}

@Singleton
class ShareTokenRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository
) {
    private val tokensCollection = firestore.collection("share_tokens")
    private val localTokens = java.util.concurrent.ConcurrentHashMap<String, ShareToken>()

    suspend fun createShareToken(bandwidthMB: Long): String {
        val hostUid = authRepository.getCurrentUid() ?: "local_host"
        val hostEmail = authRepository.getCurrentUserEmail()
        var token = generateToken()
        
        try {
            var attempts = 0
            while (attempts < 3) {
                val doc = tokensCollection.document(token).get().await()
                if (!doc.exists()) break
                token = generateToken()
                attempts++
            }

            val now = System.currentTimeMillis()
            val tokenData = ShareToken(
                token = token,
                hostUid = hostUid,
                hostEmail = hostEmail,
                guestUid = null,
                createdAt = now,
                expiresAt = now + (10 * 60 * 1000), // 10 minutes expiry
                bandwidthLimit = bandwidthMB,
                bandwidthUsed = 0L,
                status = "waiting"
            )

            tokensCollection.document(token).set(tokenData).await()
            return token
        } catch (e: Exception) {
            // Fallback: Local/Simulation mode
            val now = System.currentTimeMillis()
            val tokenData = ShareToken(
                token = token,
                hostUid = hostUid,
                hostEmail = hostEmail,
                guestUid = null,
                createdAt = now,
                expiresAt = now + (10 * 60 * 1000),
                bandwidthLimit = bandwidthMB,
                bandwidthUsed = 0L,
                status = "waiting"
            )
            localTokens[token] = tokenData
            return token
        }
    }

    private fun generateToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun validateAndJoin(token: String): JoinResult {
        val guestUid = authRepository.getCurrentUid() ?: "local_guest"
        try {
            val doc = tokensCollection.document(token).get().await()

            if (!doc.exists()) {
                val localToken = localTokens[token] ?: return JoinResult.NotFound
                val updated = localToken.copy(guestUid = guestUid, status = "connected")
                localTokens[token] = updated
                return JoinResult.Success(updated.hostUid, updated.bandwidthLimit)
            }

            val shareToken = doc.toObject(ShareToken::class.java) ?: return JoinResult.NotFound

            if (System.currentTimeMillis() > shareToken.expiresAt) {
                if (shareToken.status == "waiting") {
                    try {
                        tokensCollection.document(token).update("status", "expired").await()
                    } catch (e: Exception) {}
                }
                return JoinResult.Expired
            }

            if (shareToken.status != "waiting" || shareToken.guestUid != null) {
                return JoinResult.AlreadyUsed
            }

            if (shareToken.bandwidthUsed >= shareToken.bandwidthLimit) {
                return JoinResult.LimitReached
            }

            tokensCollection.document(token).update(
                "guestUid", guestUid,
                "status", "connected"
            ).await()

            return JoinResult.Success(shareToken.hostUid, shareToken.bandwidthLimit)
        } catch (e: Exception) {
            // Local fallback
            val localToken = localTokens[token] ?: return JoinResult.NotFound
            val updated = localToken.copy(guestUid = guestUid, status = "connected")
            localTokens[token] = updated
            return JoinResult.Success(updated.hostUid, updated.bandwidthLimit)
        }
    }

    suspend fun updateBandwidthUsed(token: String, usedBytes: Long) {
        val usedMB = usedBytes / (1024 * 1024)
        if (usedMB > 0) {
            try {
                tokensCollection.document(token).update(
                    "bandwidthUsed", FieldValue.increment(usedMB)
                ).await()
            } catch (e: Exception) {
                val localToken = localTokens[token]
                if (localToken != null) {
                    localTokens[token] = localToken.copy(bandwidthUsed = localToken.bandwidthUsed + usedMB)
                }
            }
        }
    }

    suspend fun updateTokenStatus(token: String, status: String) {
        try {
            tokensCollection.document(token).update("status", status).await()
        } catch (e: Exception) {
            val localToken = localTokens[token]
            if (localToken != null) {
                localTokens[token] = localToken.copy(status = status)
            }
        }
    }

    fun getActiveSharingTokens(): Flow<List<ShareToken>> = callbackFlow {
        val registration = try {
            tokensCollection
                .whereEqualTo("status", "waiting")
                .whereEqualTo("guestUid", null)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        val activeLocal = localTokens.values.filter {
                            it.status == "waiting" && it.guestUid == null && System.currentTimeMillis() < it.expiresAt
                        }
                        trySend(activeLocal)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val items = snapshot.toObjects(ShareToken::class.java).filter {
                            System.currentTimeMillis() < it.expiresAt
                        }
                        trySend(items)
                    } else {
                        val activeLocal = localTokens.values.filter {
                            it.status == "waiting" && it.guestUid == null && System.currentTimeMillis() < it.expiresAt
                        }
                        trySend(activeLocal)
                    }
                }
        } catch (e: Exception) {
            val job = launch(Dispatchers.Main) {
                while (true) {
                    val activeLocal = localTokens.values.filter {
                        it.status == "waiting" && it.guestUid == null && System.currentTimeMillis() < it.expiresAt
                    }
                    trySend(activeLocal)
                    delay(3000)
                }
            }
            awaitClose { job.cancel() }
            return@callbackFlow
        }
        awaitClose { registration?.remove() }
    }

    fun listenToToken(token: String): Flow<ShareToken?> = callbackFlow {
        val localToken = localTokens[token]
        if (localToken != null) {
            trySend(localToken)
            val job = launch(Dispatchers.Main) {
                while (true) {
                    delay(1000)
                    trySend(localTokens[token])
                }
            }
            awaitClose { job.cancel() }
        } else {
            val registration = try {
                tokensCollection.document(token).addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        val fallbackToken = localTokens[token]
                        if (fallbackToken != null) trySend(fallbackToken) else close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val item = snapshot.toObject(ShareToken::class.java)
                        trySend(item)
                    } else {
                        val fallbackToken = localTokens[token]
                        if (fallbackToken != null) trySend(fallbackToken) else trySend(null)
                    }
                }
            } catch (e: Exception) {
                val job = launch(Dispatchers.Main) {
                    while (true) {
                        delay(1000)
                        trySend(localTokens[token])
                    }
                }
                awaitClose { job.cancel() }
                return@callbackFlow
            }
            awaitClose { registration?.remove() }
        }
    }
}
