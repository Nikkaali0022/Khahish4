
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

private val Green = Color(0xFF18A889)
private val Ink = Color(0xFF202A35)
private val Pale = Color(0xFFF4F8F7)
private val Muted = Color(0xFF82909A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KhahishApp()
        }
    }
}

data class Chat(
    val name: String,
    val message: String,
    val time: String,
    val unread: Int = 0
)

private val demoChats = listOf(
    Chat("Areeba", "Assalamualaikum!", "2:30 PM", 2),
    Chat("Family Group", "Ammi: Ghar kab aa rahe ho?", "1:45 PM", 3),
    Chat("Hamza", "Theek hai, baat karte hain", "12:20 PM"),
    Chat("Sana", "Thank you!", "Yesterday"),
    Chat("Study Circle", "Kal class hogi", "Yesterday", 1)
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

        onDispose {
            auth.removeAuthStateListener(listener)
        }
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
fun AuthScreen(
    auth: FirebaseAuth,
    db: FirebaseFirestore
) {
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
                                        loading = false

                                        if (!saveTask.isSuccessful) {
                                            error =
                                                "Account ban gaya, lekin profile save nahi hui. Firestore rules check karein."
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
                if (isSignup)
                    "Already have an account? Login"
                else
                    "Don't have an account? Sign up",
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
    var selectedChat by remember { mutableStateOf<Chat?>(null) }

    LaunchedEffect(uid) {
        db.collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->
                userName = document.getString("name") ?: ""
            }
    }

    if (selectedChat != null) {
        ConversationScreen(
            chat = selectedChat!!,
            onBack = { selectedChat = null }
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
                    if (userName.isNotBlank()) "Welcome, $userName"
                    else email,
                    color = Muted,
                    fontSize = 12.sp
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White
            ) {
                val icons = listOf(
                    Icons.Outlined.ChatBubbleOutline,
                    Icons.Outlined.Circle,
                    Icons.Outlined.Call,
                    Icons.Outlined.Settings
                )

                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                        },
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
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { },
                    containerColor = Green,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New chat")
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
                0 -> ChatList(
                    chats = demoChats,
                    onChatClick = { selectedChat = it }
                )

                1 -> PlaceholderPage(
                    title = "Updates",
                    message = "Your updates will appear here."
                )

                2 -> PlaceholderPage(
                    title = "Calls",
                    message = "Your calls will appear here."
                )

                3 -> SettingsPage(
                    name = userName,
                    email = email,
                    onLogout = {
                        auth.signOut()
                    }
                )
            }
        }
    }
}

@Composable
fun ChatList(
    chats: List<Chat>,
    onChatClick: (Chat) -> Unit
) {
    var search by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search chats") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn {
            items(chats.filter {
                it.name.contains(search, ignoreCase = true)
            }) { chat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChatClick(chat) }
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
                            chat.name.take(1).uppercase(),
                            color = Green,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            chat.name,
                            color = Ink,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )

                        Text(
                            chat.message,
                            color = Muted,
                            fontSize = 13.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(chat.time, color = Muted, fontSize = 11.sp)

                        if (chat.unread > 0) {
                            Spacer(Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .background(Green, CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    chat.unread.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE8EFED))
            }
        }
    }
}

@Composable
fun ConversationScreen(
    chat: Chat,
    onBack: () -> Unit
) {
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pale)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            Text(
                chat.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Ink
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Your conversation will appear here.",
                color = Muted
            )
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
                onClick = {
                    message = ""
                }
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Green
                )
            }
        }
    }
}

@Composable
fun PlaceholderPage(
    title: String,
    message: String
) {
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
