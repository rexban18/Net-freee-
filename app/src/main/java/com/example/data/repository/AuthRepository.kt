package com.example.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth
) {
    private val prefs = context.getSharedPreferences("netshare_auth_prefs", Context.MODE_PRIVATE)

    fun isLoggedIn(): Boolean {
        return try {
            auth.currentUser != null || prefs.getBoolean("is_logged_in", false)
        } catch (e: Exception) {
            prefs.getBoolean("is_logged_in", false)
        }
    }

    fun getCurrentUserEmail(): String {
        return try {
            auth.currentUser?.email ?: prefs.getString("user_email", null) ?: "demo@netshare.com"
        } catch (e: Exception) {
            prefs.getString("user_email", null) ?: "demo@netshare.com"
        }
    }

    fun getCurrentUser(): FirebaseUser? {
        return try {
            auth.currentUser
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentUid(): String? {
        return try {
            auth.currentUser?.uid ?: prefs.getString("user_uid", null)
        } catch (e: Exception) {
            prefs.getString("user_uid", null)
        }
    }

    suspend fun login(email: String, pass: String): String {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val uid = result.user!!.uid
            prefs.edit().clear().apply() // clear local session if remote succeeds
            uid
        } catch (e: Exception) {
            // Local fallback login
            val mockUid = "local_uid_" + email.substringBefore("@").hashCode()
            prefs.edit()
                .putBoolean("is_logged_in", true)
                .putString("user_email", email)
                .putString("user_uid", mockUid)
                .apply()
            mockUid
        }
    }

    suspend fun register(email: String, pass: String): String {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val uid = result.user!!.uid
            prefs.edit().clear().apply() // clear local session if remote succeeds
            uid
        } catch (e: Exception) {
            // Local fallback registration
            val mockUid = "local_uid_" + email.substringBefore("@").hashCode()
            prefs.edit()
                .putBoolean("is_logged_in", true)
                .putString("user_email", email)
                .putString("user_uid", mockUid)
                .apply()
            mockUid
        }
    }

    fun logout() {
        try {
            auth.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        prefs.edit().clear().apply()
    }
}
