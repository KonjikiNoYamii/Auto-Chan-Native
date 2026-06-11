package com.silica.assistant.core.auth

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.silica.assistant.core.llm.db.UserProfileDao
import com.silica.assistant.core.llm.db.QuestDao
import com.silica.assistant.core.llm.model.UserProfileEntity
import com.silica.assistant.core.llm.model.QuestEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AuthRepository(
    private val userProfileDao: UserProfileDao,
    private val questDao: QuestDao,
    private val context: Context
) {
    private val TAG = "AuthRepository"
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: FirebaseDatabase = FirebaseDatabase.getInstance()

    fun isLoggedIn(): Boolean = auth.currentUser != null
    fun getUserId(): String? = auth.currentUser?.uid

    fun logout() {
        auth.signOut()
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val finalEmail = if (email.contains("@")) email else "$email@silica.assistant"
            Log.d(TAG, "Mencoba login: $finalEmail")
            val result = auth.signInWithEmailAndPassword(finalEmail, password).await()
            Result.success(AuthResponse(true, "Login berhasil!", userId = result.user?.uid))
        } catch (e: Exception) {
            Log.e(TAG, "Login gagal", e)
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val finalEmail = if (email.contains("@")) email else "$email@silica.assistant"
            Log.d(TAG, "Mencoba daftar: $finalEmail")
            val result = auth.createUserWithEmailAndPassword(finalEmail, password).await()
            val userId = result.user?.uid
            
            if (userId != null) {
                try {
                    Log.d(TAG, "Auth sukses, mencoba inisialisasi database untuk UID: $userId")
                    val profile = userProfileDao.getProfile() ?: UserProfileEntity()
                    
                    // Gunakan timeout agar tidak loading selamanya jika rules/koneksi bermasalah
                    withTimeout(10000) {
                        database.getReference("users").child(userId).child("profile").setValue(profile).await()
                    }
                    Log.d(TAG, "Database inisialisasi sukses")
                } catch (e: Exception) {
                    Log.e(TAG, "Database inisialisasi gagal (tapi akun sudah dibuat)", e)
                    // Kita tetap anggap sukses daftar agar user bisa coba login/sync nanti
                }
            }
            Result.success(AuthResponse(true, "Pendaftaran berhasil!", userId = userId))
        } catch (e: Exception) {
            Log.e(TAG, "Pendaftaran gagal", e)
            Result.failure(e)
        }
    }

    suspend fun syncPush(): Result<SyncResponse> = withContext(Dispatchers.IO) {
        val userId = getUserId() ?: return@withContext Result.failure(Exception("Not logged in"))
        try {
            Log.d(TAG, "Memulai sync push untuk UID: $userId")
            val profile = userProfileDao.getProfile() ?: return@withContext Result.failure(Exception("No local profile"))
            val quests = questDao.getActiveQuests().first()

            val userRef = database.getReference("users").child(userId)
            
            withTimeout(15000) {
                userRef.child("profile").setValue(profile).await()
                userRef.child("quests").setValue(quests).await()
            }

            Result.success(SyncResponse(true, "Progress & Quest berhasil diunggah!"))
        } catch (e: Exception) {
            Log.e(TAG, "Sync push gagal", e)
            Result.failure(e)
        }
    }

    suspend fun syncPull(): Result<UserProfileEntity> = withContext(Dispatchers.IO) {
        val userId = getUserId() ?: return@withContext Result.failure(Exception("Not logged in"))
        try {
            Log.d(TAG, "Memulai sync pull untuk UID: $userId")
            val userRef = database.getReference("users").child(userId)

            val profileSnapshot = withTimeout(15000) {
                userRef.child("profile").get().await()
            }
            val remoteProfile = profileSnapshot.getValue(UserProfileEntity::class.java) 
                ?: return@withContext Result.failure(Exception("No remote profile found"))

            val questsSnapshot = withTimeout(15000) {
                userRef.child("quests").get().await()
            }
            val remoteQuests = questsSnapshot.children.mapNotNull { it.getValue(QuestEntity::class.java) }

            userProfileDao.updateProfile(remoteProfile)
            remoteQuests.forEach { questDao.insertQuest(it) }

            Result.success(remoteProfile)
        } catch (e: Exception) {
            Log.e(TAG, "Sync pull gagal", e)
            Result.failure(e)
        }
    }
}
