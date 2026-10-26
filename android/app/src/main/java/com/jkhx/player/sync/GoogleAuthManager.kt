package com.jkhx.player.sync

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.jkhx.player.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthManager private constructor(private val context: Context) {

    private val driveScope = "https://www.googleapis.com/auth/drive.appdata"
    private val scopeString = "oauth2:$driveScope"

    private val gso: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestProfile()
        .requestScopes(Scope(driveScope))
        .build()

    private val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    val signInIntent: Intent
        get() = client.signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun isSignedIn(): Boolean {
        return getLastSignedInAccount() != null
    }

    fun handleSignInSuccess(account: GoogleSignInAccount) {
        val settings = SettingsManager.getInstance(context)
        settings.setGoogleAccount(
            email = account.email,
            name = account.displayName,
            photoUrl = account.photoUrl?.toString()
        )
    }

    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = getLastSignedInAccount() ?: return@withContext null
        val androidAccount = account.account ?: Account(account.email ?: return@withContext null, "com.google")
        try {
            GoogleAuthUtil.getToken(context, androidAccount, scopeString)
        } catch (e: Exception) {
            println("[GoogleAuthManager] Error getting access token: ${e.message}")
            null
        }
    }

    suspend fun invalidateToken(token: String) = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            println("[GoogleAuthManager] Error clearing token: ${e.message}")
        }
    }

    fun signOut(onComplete: (() -> Unit)? = null) {
        client.signOut().addOnCompleteListener {
            val settings = SettingsManager.getInstance(context)
            settings.clearGoogleAccount()
            onComplete?.invoke()
        }
    }

    companion object {
        @Volatile
        private var instance: GoogleAuthManager? = null

        fun getInstance(context: Context): GoogleAuthManager {
            return instance ?: synchronized(this) {
                instance ?: GoogleAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

