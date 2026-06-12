package com.silica.assistant.core.auth

import android.util.Log
import com.google.firebase.database.*
import com.silica.assistant.core.llm.db.FriendDao
import com.silica.assistant.core.llm.db.SocialMessageDao
import com.silica.assistant.core.llm.model.FriendEntity
import com.silica.assistant.core.llm.model.SocialMessageEntity
import com.silica.assistant.core.llm.model.UserProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SocialRepository(
    private val authRepository: AuthRepository,
    private val friendDao: FriendDao,
    private val socialMessageDao: SocialMessageDao
) {
    private val TAG = "SocialRepository"
    private val database = FirebaseDatabase.getInstance()

    fun getCurrentUserId(): String? = authRepository.getUserId()

    suspend fun searchUsers(query: String): List<UserProfileEntity> = withContext(Dispatchers.IO) {
        try {
            val snapshot = database.getReference("users")
                .orderByChild("profile/userName")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .limitToFirst(20)
                .get().await()

            snapshot.children.mapNotNull { 
                val profile = it.child("profile").getValue(UserProfileEntity::class.java)
                // Add userId to profile if not present (assuming we might need it)
                profile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mencari user", e)
            emptyList()
        }
    }

    suspend fun sendFriendRequest(otherUserId: String, otherNickname: String) = withContext(Dispatchers.IO) {
        val currentUserId = getCurrentUserId() ?: return@withContext
        val requestRef = database.getReference("friend_requests").child(otherUserId).child(currentUserId)
        requestRef.setValue(mapOf(
            "userId" to currentUserId,
            "nickname" to (authRepository.isLoggedIn().let { "User" }), // Should get current user nickname
            "timestamp" to ServerValue.TIMESTAMP
        )).await()
    }

    fun observeFriendRequests(): Flow<List<Map<String, Any>>> = callbackFlow {
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            close()
            return@callbackFlow
        }
        val ref = database.getReference("friend_requests").child(currentUserId)
        
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val requests = snapshot.children.mapNotNull { it.value as? Map<String, Any> }
                trySend(requests)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun acceptFriendRequest(otherUserId: String, otherNickname: String) = withContext(Dispatchers.IO) {
        val currentUserId = getCurrentUserId() ?: return@withContext
        
        // Add to friends node for both users
        val myFriendsRef = database.getReference("friends").child(currentUserId).child(otherUserId)
        val otherFriendsRef = database.getReference("friends").child(otherUserId).child(currentUserId)
        
        val timestamp = ServerValue.TIMESTAMP
        myFriendsRef.setValue(mapOf("userId" to otherUserId, "nickname" to otherNickname, "status" to "ACCEPTED", "timestamp" to timestamp))
        otherFriendsRef.setValue(mapOf("userId" to currentUserId, "nickname" to "User", "status" to "ACCEPTED", "timestamp" to timestamp))
        
        // Remove from friend requests
        database.getReference("friend_requests").child(currentUserId).child(otherUserId).removeValue().await()
        
        // Save to local DB
        friendDao.insertFriend(FriendEntity(userId = otherUserId, nickname = otherNickname, status = "ACCEPTED"))
    }

    fun observeMessages(otherUserId: String): Flow<List<SocialMessageEntity>> = callbackFlow {
        val currentUserId = getCurrentUserId()
        if (currentUserId == null) {
            close()
            return@callbackFlow
        }
        val chatId = if (currentUserId.compareTo(otherUserId) < 0) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        val ref = database.getReference("messages").child(chatId)
        
        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val msg = snapshot.getValue(SocialMessageEntity::class.java)
                if (msg != null) {
                    trySend(listOf(msg))
                    // Also save to local DB
                    // Note: This might cause duplicates if not handled, but Room insert usually handles it if IDs match.
                    // However, Firebase doesn't auto-generate Long IDs like Room.
                }
            }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        ref.addChildEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendMessage(otherUserId: String, content: String) = withContext(Dispatchers.IO) {
        val currentUserId = getCurrentUserId() ?: return@withContext
        val chatId = if (currentUserId.compareTo(otherUserId) < 0) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        val ref = database.getReference("messages").child(chatId).push()
        
        val message = mapOf(
            "chatId" to chatId,
            "senderId" to currentUserId,
            "content" to content,
            "timestamp" to ServerValue.TIMESTAMP,
            "isRead" to false
        )
        ref.setValue(message).await()
    }
}
