
package com.khahish.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Green = Color(0xFF18A889)
private val Ink = Color(0xFF202A35)
private val Pale = Color(0xFFF4F8F7)
private val Muted = Color(0xFF82909A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KhahishApp() }
    }
}

data class UserProfile(
    val uid: String,
    val name: String
)

data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Long
)

@Composable
fun KhahishApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener {
            currentUser = it.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            background = Pale,
            surface = Color.White
        )
    ) {
        if (currentUser == null) {
            AuthScreen(auth, db)
        } else {
            MainHome(
                auth = auth,
                db = db,
                uid = currentUser!!.uid,
                email = currentUser!!.email ?: ""
            )
        }
    }
}

@Composable
fun AuthScreen(auth: FirebaseAuth, db: FirebaseFirestore) {
    var isSignup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pale)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Green, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "K",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(18.dp))

        Text(
            "Khahish",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        Text(
            if (isSignup) "Create your account" else "Welcome back",
            color = Muted,
            fontSize = 15.sp
        )

        Spacer(Modifier.height(28.dp))

        if (isSignup) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            )
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        if (isSignup) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text("Confirm Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
        }

        if (error.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Color.Red, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                error = ""

                if (email.isBlank() || password.isBlank()) {
                    error = "Email aur password zaroori hain."
                    return@Button
                }

                if (isSignup) {
                    if (name.isBlank()) {
                        error = "Apna naam enter karein."
                        return@Button
                    }
                    if (password.length < 6) {
                        error = "Password kam az kam 6 characters ka ho."
                        return@Button
                    }
                    if (password != confirmPassword) {
                        error = "Dono passwords match nahi karte."
                        return@Button
                    }
                }

                loading = true

                if (isSignup) {
                    auth.createUserWithEmailAndPassword(
                        email.trim(),
                        password
                    ).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser

                            if (user != null) {
                                val profile = hashMapOf(
                                    "uid" to user.uid,
                                    "name" to name.trim(),
                                    "email" to email.trim(),
                                    "createdAt" to System.currentTimeMillis()
                                )

                                db.collection("users")
                                    .document(user.uid)
                                    .set(profile)
                                    .addOnCompleteListener { saveTask ->
                                        if (!saveTask.isSuccessful) {
                                            loading = false
                                            error = "Profile save nahi hui: ${
                                                saveTask.exception?.localizedMessage
                                            }"
                                        } else {
                                            val publicProfile = hashMapOf(
                                                "uid" to user.uid,
                                                "name" to name.trim()
                                            )

                                            db.collection("publicProfiles")
                                                .document(user.uid)
                                                .set(publicProfile)
                                                .addOnCompleteListener { publicTask ->
                                                    loading = false
                                                    if (!publicTask.isSuccessful) {
                                                        error = "Public profile save nahi hui: ${
                                                            publicTask.exception?.localizedMessage
                                                        }"
                                                    }
                                                }
                                        }
                                    }
                            } else {
                                loading = false
                            }
                        } else {
                            loading = false
                            error = task.exception?.localizedMessage
                                ?: "Signup nahi ho saka."
                        }
                    }
                } else {
                    auth.signInWithEmailAndPassword(
                        email.trim(),
                        password
                    ).addOnCompleteListener { task ->
                        loading = false
                        if (!task.isSuccessful) {
                            error = task.exception?.localizedMessage
                                ?: "Login nahi ho saka."
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !loading,
            colors = ButtonDefaults.buttonColors(containerColor = Green),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    if (isSignup) "Create Account" else "Login",
                    fontSize = 16.sp
                )
            }
        }

        TextButton(
            onClick = {
                isSignup = !isSignup
                error = ""
            }
        ) {
            Text(
                if (isSignup) "Already have an account? Login"
                else "Don't have an account? Sign up",
                color = Green
            )
        }
    }
}

@Composable
fun MainHome(
    auth: FirebaseAuth,
    db: FirebaseFirestore,
    uid: String,
    email: String
) {
    var userName by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var selectedConversation by remember { mutableStateOf("") }
    var openingError by remember { mutableStateOf("") }

    // Ensure older accounts also get a public profile.
    LaunchedEffect(uid) {
        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                val name = document.getString("name") ?: "Khahish User"
                userName = name

                val profile = hashMapOf(
                    "uid" to uid,
                    "name" to name
                )

                db.collection("publicProfiles")
                    .document(uid)
                    .set(profile)
            }
    }

    if (selectedUser != null) {
        ConversationScreen(
            conversationId = selectedConversation,
            currentUid = uid,
            otherUser = selectedUser!!,
            db = db,
            onBack = {
                selectedUser = null
                selectedConversation = ""
            }
        )
        return
    }

    val tabs = listOf("Chats", "Updates", "Calls", "Settings")

    Scaffold(
        containerColor = Pale,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    "Khahish",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    color = Green
                )
                Text(
                    if (userName.isNotBlank()) "Welcome, $userName" else email,
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val icons = listOf(
                    Icons.Outlined.ChatBubbleOutline,
                    Icons.Outlined.Circle,
                    Icons.Outlined.Call,
                    Icons.Outlined.Settings
                )

                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(icons[index], contentDescription = label)
                        },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Green,
                            selectedTextColor = Green,
                            indicatorColor = Green.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> UsersScreen(
                    db = db,
                    currentUid = uid,
                    onUserClick = { otherUser ->
                        openingError = ""

                        val conversationId = listOf(uid, otherUser.uid)
                            .sorted()
                            .joinToString("_")

                        val conversationRef = db.collection("conversations")
                            .document(conversationId)

                        conversationRef.get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    selectedConversation = conversationId
                                    selectedUser = otherUser
                                } else {
                                    val conversation = hashMapOf(
                                        "participants" to listOf(uid, otherUser.uid),
                                        "createdAt" to System.currentTimeMillis(),
                                        "lastMessage" to ""
                                    )

                                    conversationRef.set(conversation)
                                        .addOnSuccessListener {
                                            selectedConversation = conversationId
                                            selectedUser = otherUser
                                        }
                                        .addOnFailureListener {
                                            openingError = it.localizedMessage
                                                ?: "Chat open nahi ho saki."
                                        }
                                }
                            }
                            .addOnFailureListener {
                                openingError = it.localizedMessage
                                    ?: "Chat open nahi ho saki."
                            }
                    }
                )

                1 -> PlaceholderPage(
                    "Updates",
                    "Your updates will appear here."
                )

                2 -> PlaceholderPage(
                    "Calls",
                    "Your calls will appear here."
                )

                3 -> SettingsPage(
                    name = userName,
                    email = email,
                    onLogout = { auth.signOut() }
                )
            }

            if (openingError.isNotBlank()) {
                Text(
                    openingError,
                    color = Color.Red,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun UsersScreen(
    db: FirebaseFirestore,
    currentUid: String,
    onUserClick: (UserProfile) -> Unit
) {
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(currentUid) {
        val registration: ListenerRegistration =
            db.collection("publicProfiles")
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        loading = false
                        error = exception.localizedMessage
                            ?: "Users load nahi ho sake."
                    } else if (snapshot != null) {
                        users = snapshot.documents.mapNotNull { document ->
                            val id = document.getString("uid") ?: document.id
                            val name = document.getString("name") ?: "Khahish User"

                            if (id == currentUid) null
                            else UserProfile(id, name)
                        }
                        loading = false
                        error = ""
                    }
                }

        onDispose { registration.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text(
            "People on Khahish",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search users") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(12.dp))

        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Green)
            }

            error.isNotBlank() -> Text(error, color = Color.Red)

            users.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Abhi koi doosra user registered nahi hai.",
                    color = Muted
                )
            }

            else -> LazyColumn {
                items(
                    users.filter {
                        it.name.contains(search, ignoreCase = true)
                    },
                    key = { it.uid }
                ) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUserClick(user) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    Green.copy(alpha = 0.14f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                user.name.take(1).uppercase(),
                                color = Green,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                user.name,
                                color = Ink,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Text(
                                "Tap to start a private chat",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Muted
                        )
                    }

                    HorizontalDivider(color = Color(0xFFE8EFED))
                }
            }
        }
    }
}

@Composable
fun ConversationScreen(
    conversationId: String,
    currentUid: String,
    otherUser: UserProfile,
    db: FirebaseFirestore,
    onBack: () -> Unit
) {
    var message by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(conversationId) {
        val registration = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.localizedMessage
                        ?: "Messages load nahi ho sake."
                } else if (snapshot != null) {
                    messages = snapshot.documents.mapNotNull { document ->
                        val sender = document.getString("senderId") ?: return@mapNotNull null
                        val text = document.getString("text") ?: return@mapNotNull null
                        val time = document.getLong("createdAt") ?: 0L

                        Message(
                            id = document.id,
                            senderId = sender,
                            text = text,
                            createdAt = time
                        )
                    }
                    error = ""
                }
            }

        onDispose { registration.remove() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pale)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Green.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    otherUser.name.take(1).uppercase(),
                    color = Green,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(10.dp))

            Text(
                otherUser.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
        }

        if (error.isNotBlank()) {
            Text(
                error,
                color = Color.Red,
                modifier = Modifier.padding(8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            reverseLayout = false
        ) {
            items(messages, key = { it.id }) { item ->
                val mine = item.senderId == currentUid

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = if (mine) {
                        Alignment.End
                    } else {
                        Alignment.Start
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 290.dp)
                            .background(
                                if (mine) Green else Color.White,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    ) {
                        Text(
                            item.text,
                            color = if (mine) Color.White else Ink,
                            fontSize = 15.sp
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            if (item.createdAt > 0) {
                                SimpleDateFormat(
                                    "hh:mm a",
                                    Locale.getDefault()
                                ).format(Date(item.createdAt))
                            } else "",
                            color = if (mine) Color.White.copy(alpha = 0.75f)
                            else Muted,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            IconButton(
                enabled = message.isNotBlank() && !sending,
                onClick = {
                    val textToSend = message.trim()
                    if (textToSend.isNotEmpty()) {
                        sending = true
                        error = ""

                        val messageData = hashMapOf(
                            "senderId" to currentUid,
                            "text" to textToSend,
                            "createdAt" to System.currentTimeMillis()
                        )

                        db.collection("conversations")
                            .document(conversationId)
                            .collection("messages")
                            .add(messageData)
                            .addOnSuccessListener {
                                db.collection("conversations")
                                    .document(conversationId)
                                    .update(
                                        mapOf(
                                            "lastMessage" to textToSend,
                                            "lastMessageAt" to System.currentTimeMillis()
                                        )
                                    )
                                    .addOnCompleteListener {
                                        sending = false
                                        message = ""
                                    }
                            }
                            .addOnFailureListener {
                                sending = false
                                error = it.localizedMessage
                                    ?: "Message send nahi ho saka."
                            }
                    }
                }
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Green,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Green
                    )
                }
            }
        }
    }
}

@Composable
fun PlaceholderPage(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted)
    }
}

@Composable
fun SettingsPage(
    name: String,
    email: String,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        Text(
            "Settings",
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Green.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Green
                )
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    name.ifBlank { "Khahish User" },
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Text(email, color = Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLogout() }
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Logout,
                contentDescription = null,
                tint = Color.Red
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "Logout",
                color = Color.Red,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
