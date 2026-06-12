package com.silica.assistant.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.silica.assistant.core.auth.SocialRepository
import com.silica.assistant.core.llm.db.FriendDao
import com.silica.assistant.core.llm.model.FriendEntity
import com.silica.assistant.core.llm.model.UserProfileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SocialViewModel(
    private val socialRepository: SocialRepository,
    private val friendDao: FriendDao
) : ViewModel() {

    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<UserProfileEntity>>(emptyList())
    var isSearching by mutableStateOf(false)

    val friends: StateFlow<List<FriendEntity>> = friendDao.getAllFriends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val friendRequests: StateFlow<List<Map<String, Any>>> = socialRepository.observeFriendRequests()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun searchUsers() {
        if (searchQuery.isBlank()) return
        isSearching = true
        viewModelScope.launch {
            searchResults = socialRepository.searchUsers(searchQuery)
            isSearching = false
        }
    }

    fun sendFriendRequest(userId: String, nickname: String) {
        viewModelScope.launch {
            socialRepository.sendFriendRequest(userId, nickname)
        }
    }

    fun acceptFriendRequest(userId: String, nickname: String) {
        viewModelScope.launch {
            socialRepository.acceptFriendRequest(userId, nickname)
        }
    }
}
