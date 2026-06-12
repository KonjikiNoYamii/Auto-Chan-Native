package com.silica.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silica.assistant.core.llm.model.FriendEntity
import com.silica.assistant.core.llm.model.UserProfileEntity
import com.silica.assistant.ui.theme.DeepRose
import com.silica.assistant.ui.theme.Espresso
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    onBack: () -> Unit,
    onViewProfile: (UserProfileEntity) -> Unit,
    onChat: (FriendEntity) -> Unit,
    viewModel: SocialViewModel = koinViewModel()
) {
    val friends by viewModel.friends.collectAsState()
    val requests by viewModel.friendRequests.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teman & Sosial", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SearchSection(viewModel, onViewProfile)

            if (requests.isNotEmpty()) {
                RequestSection(requests, viewModel)
            }

            Text(
                "Daftar Teman (${friends.size})",
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Bold,
                color = Espresso,
                fontSize = 14.sp
            )

            if (friends.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Belum ada teman. Cari user lain untuk berteman! ♪", color = Color.Gray, fontSize = 12.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(friends) { friend ->
                        FriendItem(friend, onChat)
                    }
                }
            }
        }
    }
}

@Composable
fun SearchSection(viewModel: SocialViewModel, onViewProfile: (UserProfileEntity) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cari username...") },
            trailingIcon = {
                IconButton(onClick = { viewModel.searchUsers() }) {
                    Icon(Icons.Default.Search, null)
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
        
        if (viewModel.isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = DeepRose)
        }

        Spacer(modifier = Modifier.height(8.dp))

        viewModel.searchResults.forEach { user ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onViewProfile(user) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(40.dp).background(DeepRose.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(user.userName.take(1).uppercase(), color = DeepRose, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(user.userName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Lv. ${user.level} • AI: ${user.aiName}", fontSize = 12.sp, color = Color.Gray)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.sendFriendRequest(user.userName, user.userName) }) { // Use actual UID in real scenario
                    Icon(Icons.Default.PersonAdd, null, tint = DeepRose)
                }
            }
        }
    }
}

@Composable
fun RequestSection(requests: List<Map<String, Any>>, viewModel: SocialViewModel) {
    Column(modifier = Modifier.fillMaxWidth().background(DeepRose.copy(alpha = 0.05f)).padding(16.dp)) {
        Text("Permintaan Pertemanan", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DeepRose)
        Spacer(modifier = Modifier.height(8.dp))
        requests.forEach { req ->
            val nickname = req["nickname"] as? String ?: "User"
            val userId = req["userId"] as? String ?: ""
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(nickname, modifier = Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = { viewModel.acceptFriendRequest(userId, nickname) }) {
                    Text("Terima", color = DeepRose)
                }
            }
        }
    }
}

@Composable
fun FriendItem(friend: FriendEntity, onChat: (FriendEntity) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChat(friend) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).background(Espresso.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
            Text(friend.nickname.take(1).uppercase(), color = Espresso, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(friend.nickname, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Espresso)
            Text(friend.lastMessage ?: "Belum ada pesan", fontSize = 12.sp, color = Color.Gray, maxLines = 1)
        }
        if (friend.lastMessageTime > 0) {
            Text("Baru saja", fontSize = 10.sp, color = Color.LightGray)
        }
    }
}
