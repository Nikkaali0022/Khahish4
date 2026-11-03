
package com.khahish.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
    val name: String,
    val username: String
)

data class Message(
    val id: String,
    val senderId: String,
    val text: String,
    val createdAt: Long
)

data class RecentChat(
    val id: String,
    val otherUid: String,
    val otherName: String,
    val lastMessage: String,
    val lastMessageAt: Long
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
            color = Muted
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
            Text(error, color = Color.Red)
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
                        error = "Passwords match nahi karte."
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
                                        loading = false
                                        if (!saveTask.isSuccessful) {
                                            error = saveTask.exception
                                                ?.localizedMessage
                                                ?: "Profile save nahi hui."
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
            colors = ButtonDefaults.buttonColors(
                containerColor = Green
            ),
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
                    if (isSignup) "Create Account" else "Login"
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
    var username by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedUser by remember { mutableStateOf<UserProfile?>(null) }
    var selectedConversation by remember { mutableStateOf("") }
    var openingError by remember { mutableStateOf("") }
    var profileLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                userName = document.getString("name") ?: "Khahish User"
                profileLoaded = true
            }
            .addOnFailureListener {
                openingError = it.localizedMessage
                    ?: "Profile load nahi hui."
                profileLoaded = true
            }
    }

    BackHandler(enabled = selectedUser != null) {
        selectedUser = null
        selectedConversation = ""
        openingError = ""
    }

    if (!profileLoaded) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Green)
        }
        return
    }

    if (username.isBlank()) {
        UsernameSetupScreen(
            uid = uid,
            name = userName,
            db = db,
            onComplete = { value ->
                username = value
            }
        )
        return
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

    val tabs = listOf("Chats", "Search", "Settings")

    Scaffold(
        containerColor = Pale,
        topBar = {
            Column(
                Modifier
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
                    "Welcome, $userName  •  @$username",
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val icons = listOf(
                    Icons.Default.Chat,
                    Icons.Default.Search,
                    Icons.Default.Settings
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
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> RecentChatsScreen(
                    db = db,
                    uid = uid,
                    onChatClick = { chat ->
                        selectedConversation = chat.id
                        selectedUser = UserProfile(
                            chat.otherUid,
                            chat.otherName,
                            ""
                        )
                    }
                )

                1 -> UsernameSearchScreen(
                    db = db,
                    currentUid = uid,
                    onUserClick = { otherUser ->
                        openingError = ""

                        val participants = listOf(
                            uid,
                            otherUser.uid
                        ).sorted()

                        val conversationId =
                            participants.joinToString("_")

                        val conversationRef = db.collection("conversations")
                            .document(conversationId)

                        val conversation = hashMapOf(
                            "participants" to participants,
                            "createdAt" to System.currentTimeMillis()
                        )

                        conversationRef.set(
                            conversation,
                            com.google.firebase.firestore.SetOptions.merge()
                        ).addOnSuccessListener {
                            selectedConversation = conversationId
                            selectedUser = otherUser
                        }.addOnFailureListener {
                            openingError = it.localizedMessage
                                ?: "Chat open nahi ho saki."
                        }
                    }
                )

                2 -> SettingsPage(
                    name = userName,
                    username = username,
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
fun UsernameSetupScreen(
    uid: String,
    name: String,
    db: FirebaseFirestore,
    onComplete: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pale)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Choose your username",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Doosre log aapko isi username se search karenge.",
            color = Muted
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it.lowercase().replace(" ", "")
                error = ""
            },
            label = { Text("Username") },
            leadingIcon = { Text("@") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "3–20 characters. Letters, numbers aur underscore.",
            color = Muted,
            fontSize = 12.sp
        )

        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Color.Red)
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                val value = input.trim().removePrefix("@").lowercase()

                if (!value.matches(Regex("^[a-z0-9_]{3,20}$"))) {
                    error = "Username 3–20 characters ka hona chahiye."
                    return@Button
                }

                loading = true
                error = ""

                val usernameRef = db.collection("usernames")
                    .document(value)

                val mappingRef = db.collection("usernamesByUid")
                    .document(uid)

                db.runTransaction { transaction ->
                    val existing = transaction.get(usernameRef)

                    if (existing.exists()) {
                        throw IllegalStateException(
                            "Ye username pehle se use ho raha hai."
                        )
                    }

                    val data = hashMapOf(
                        "uid" to uid,
                        "name" to name,
                        "username" to value
                    )

                    transaction.set(usernameRef, data)
                    transaction.set(mappingRef, data)

                    transaction.set(
                        db.collection("users").document(uid),
                        mapOf("username" to value),
                        com.google.firebase.firestore.SetOptions.merge()
                    )

                    null
                }.addOnSuccessListener {
                    loading = false
                    onComplete(value)
                }.addOnFailureListener {
                    loading = false
                    error = it.localizedMessage
                        ?: "Username save nahi ho saka."
                }
            },
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Green
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White
                )
            } else {
                Text("Save Username")
            }
        }
    }
}

@Composable
fun UsernameSearchScreen(
    db: FirebaseFirestore,
    currentUid: String,
    onUserClick: (UserProfile) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Find people",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = search,
            onValueChange = {
                search = it
                result = null
                searched = false
                error = ""
            },
            placeholder = { Text("Enter exact username") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                val value = search.trim()
                    .removePrefix("@")
                    .lowercase()

                if (!value.matches(Regex("^[a-z0-9_]{3,20}$"))) {
                    error = "Valid username enter karein."
                    return@Button
                }

                loading = true
                searched = false
                result = null
                error = ""

                db.collection("usernames")
                    .document(value)
                    .get()
                    .addOnSuccessListener { document ->
                        loading = false
                        searched = true

                        if (document.exists()) {
                            val id = document.getString("uid") ?: ""
                            val name = document.getString("name")
                                ?: "Khahish User"

                            if (id == currentUid) {
                                error = "Ye aapka apna username hai."
                            } else {
                                result = UserProfile(id, name, value)
                            }
                        }
                    }
                    .addOnFailureListener {
                        loading = false
                        error = it.localizedMessage
                            ?: "Search nahi ho saki."
                    }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Green
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White
                )
            } else {
                Text("Search")
            }
        }

        if (error.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(error, color = Color.Red)
        }

        if (searched && result == null && error.isBlank()) {
            Spacer(Modifier.height(18.dp))
            Text("Is username ka user nahi mila.", color = Muted)
        }

        result?.let { user ->
            Spacer(Modifier.height(18.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .clickable { onUserClick(user) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(user.name)

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        user.name,
                        fontWeight = FontWeight.Bold,
                        color = Ink
                    )
                    Text("@${user.username}", color = Muted)
                }

                Icon(
                    Icons.Default.Chat,
                    contentDescription = "Open chat",
                    tint = Green
                )
            }
        }
    }
}

@Composable
fun RecentChatsScreen(
    db: FirebaseFirestore,
    uid: String,
    onChatClick: (RecentChat) -> Unit
) {
    var chats by remember { mutableStateOf<List<RecentChat>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    DisposableEffect(uid) {
        val registration: ListenerRegistration =
            db.collection("conversations")
                .whereArrayContains("participants", uid)
                .addSnapshotListener { snapshot, exception ->
                    if (exception != null) {
                        loading = false
                        error = exception.localizedMessage
                            ?: "Chats load nahi ho sakin."
                    } else if (snapshot != null) {
                        loading = false
                        error = ""

                        snapshot.documents.forEach { document ->
                            val participants =
                                document.get("participants") as? List<*>
                                    ?: emptyList<Any>()

                            val otherUid = participants
                                .filterIsInstance<String>()
                                .firstOrNull { it != uid }
                                ?: return@forEach

                            db.collection("usernamesByUid")
                                .document(otherUid)
                                .get()
                                .addOnSuccessListener { profile ->
                                    val name = profile.getString("name")
                                        ?: "Khahish User"

                                    val chat = RecentChat(
                                        id = document.id,
                                        otherUid = otherUid,
                                        otherName = name,
                                        lastMessage = document.getString(
                                            "lastMessage"
                                        ) ?: "Start chatting",
                                        lastMessageAt = document.getLong(
                                            "lastMessageAt"
                                        ) ?: document.getLong("createdAt") ?: 0L
                                    )

                                    chats = (chats.filterNot {
                                        it.id == chat.id
                                    } + chat).sortedByDescending {
                                        it.lastMessageAt
                                    }
                                }
                        }
                    }
                }

        onDispose { registration.remove() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text(
            "Recent Chats",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Ink
        )

        Spacer(Modifier.height(12.dp))

        when {
            loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Green)
            }

            error.isNotBlank() -> Text(error, color = Color.Red)

            chats.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Abhi koi chat nahi. Search se user dhoondein.",
                    color = Muted
                )
            }

            else -> LazyColumn {
                items(chats, key = { it.id }) { chat ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChatClick(chat) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(chat.otherName)

                        Spacer(Modifier.width(12.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                chat.otherName,
                                color = Ink,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                chat.lastMessage,
                                color = Muted,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }

                        if (chat.lastMessageAt > 0) {
                            Text(
                                formatTime(chat.lastMessageAt),
                                color = Muted,
                                fontSize = 10.sp
                            )
                        }
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

    BackHandler(onBack = onBack)

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
                        val sender = document.getString("senderId")
                            ?: return@mapNotNull null
                        val text = document.getString("text")
                            ?: return@mapNotNull null
                        val time = document.getLong("createdAt") ?: 0L

                        Message(
                            document.id,
                            sender,
                            text,
                            time
                        )
                    }
                    error = ""
                }
            }

        onDispose { registration.remove() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Pale)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            UserAvatar(otherUser.name)

            Spacer(Modifier.width(10.dp))

            Column {
                Text(
                    otherUser.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                if (otherUser.username.isNotBlank()) {
                    Text("@${otherUser.username}", color = Muted, fontSize = 11.sp)
                }
            }
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
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = if (mine) {
                        Alignment.End
                    } else {
                        Alignment.Start
                    }
                ) {
                    Column(
                        Modifier
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
                            if (item.createdAt > 0) formatTime(item.createdAt)
                            else "",
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
            Modifier
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

                        val now = System.currentTimeMillis()

                        val messageData = hashMapOf(
                            "senderId" to currentUid,
                            "text" to textToSend,
                            "createdAt" to now
                        )

                        val conversationRef = db.collection("conversations")
                            .document(conversationId)

                        conversationRef.collection("messages")
                            .add(messageData)
                            .addOnSuccessListener {
                                conversationRef.update(
                                    mapOf(
                                        "lastMessage" to textToSend,
                                        "lastMessageAt" to now
                                    )
                                ).addOnCompleteListener { updateTask ->
                                    sending = false
                                    message = ""

                                    if (!updateTask.isSuccessful) {
                                        error = updateTask.exception
                                            ?.localizedMessage
                                            ?: "Chat update nahi hui."
                                    }
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
fun UserAvatar(name: String) {
    Box(
        Modifier
            .size(48.dp)
            .background(Green.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).uppercase(),
            color = Green,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp
        )
    }
}

fun formatTime(time: Long): String {
    return SimpleDateFormat(
        "hh:mm a",
        Locale.getDefault()
    ).format(Date(time))
}

@Composable
fun SettingsPage(
    name: String,
    username: String,
    email: String,
    onLogout: () -> Unit
) {
    Column(
        Modifier
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
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(14.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(name)

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    name.ifBlank { "Khahish User" },
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
                Text("@$username", color = Muted, fontSize = 12.sp)
                Text(email, color = Muted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier
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
