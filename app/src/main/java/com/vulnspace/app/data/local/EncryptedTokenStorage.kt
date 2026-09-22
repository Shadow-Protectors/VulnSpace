package com.vulnspace.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure local storage for Community Head application tracking tokens.
 * Uses Android Keystore-backed AES-256 GCM encryption.
 */
class EncryptedTokenStorage(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for testing environments or keystore recovery
        context.getSharedPreferences(FALLBACK_PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun saveTrackedApplication(
        token: String,
        applicationId: String,
        communityName: String,
        applicantEmail: String? = null
    ) {
        sharedPreferences.edit()
            .putString(KEY_TRACKING_TOKEN, token.trim())
            .putString(KEY_APPLICATION_ID, applicationId.trim())
            .putString(KEY_COMMUNITY_NAME, communityName.trim())
            .apply {
                if (applicantEmail != null) {
                    putString(KEY_APPLICANT_EMAIL, applicantEmail.trim().lowercase())
                } else {
                    remove(KEY_APPLICANT_EMAIL)
                }
            }
            .apply()
    }

    fun getTrackingToken(): String? = sharedPreferences.getString(KEY_TRACKING_TOKEN, null)

    fun getApplicationId(): String? = sharedPreferences.getString(KEY_APPLICATION_ID, null)

    fun getCommunityName(): String? = sharedPreferences.getString(KEY_COMMUNITY_NAME, null)

    fun getApplicantEmail(): String? = sharedPreferences.getString(KEY_APPLICANT_EMAIL, null)

    fun hasTrackedApplication(): Boolean = !getTrackingToken().isNullOrBlank()

    fun clearTrackedApplication() {
        sharedPreferences.edit()
            .remove(KEY_TRACKING_TOKEN)
            .remove(KEY_APPLICATION_ID)
            .remove(KEY_COMMUNITY_NAME)
            .remove(KEY_APPLICANT_EMAIL)
            .apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "vulnspace_secure_tokens"
        private const val FALLBACK_PREFS_FILE = "vulnspace_fallback_tokens"
        private const val KEY_TRACKING_TOKEN = "head_app_tracking_token"
        private const val KEY_APPLICATION_ID = "head_app_id"
        private const val KEY_COMMUNITY_NAME = "head_app_community_name"
        private const val KEY_APPLICANT_EMAIL = "head_app_applicant_email"
    }
}
