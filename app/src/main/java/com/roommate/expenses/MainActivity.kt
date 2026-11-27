package com.roommate.expenses

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

data class Member(val uid: String = "", val name: String = "", val whatsapp: String = "", val role: String = "member")
data class Expense(val id: String = "", val memberUid: String = "", val memberName: String = "", val item: String = "", val amount: Double = 0.0, val category: String = "Other", val date: Long = System.currentTimeMillis())

private val Purple = Color(0xFF6542B8)
private val Pale = Color(0xFFF4F0FC)
private val EmailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

private fun isValidEmail(value: String) = EmailPattern.matches(value.trim())

private fun passwordIssue(value: String): String? = when {
    value.length < 8 -> "Password must be at least 8 characters"
    !value.any { it.isLetter() } -> "Password must include at least one letter"
    !value.any { it.isDigit() } -> "Password must include at least one number"
    else -> null
}

private fun friendlyAuthError(error: Exception): String = when (error) {
    is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
    is FirebaseAuthInvalidUserException -> "No account found for this email."
    is FirebaseAuthUserCollisionException -> "An account already exists with this email."
    is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 8 characters with letters and numbers."
    else -> when {
        error.message?.contains("network", true) == true -> "No internet connection. Please try again."
        error.message?.contains("too-many-requests", true) == true -> "Too many attempts. Please wait and try again."
        else -> error.message ?: "Authentication failed. Please try again."
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContent { RoomTheme { RoomMateApp() } }
    }
}

@Composable private fun RoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Purple, onPrimary = Color.White, secondary = Color(0xFF9274D4), background = Color(0xFFFAF9FC), surface = Color.White), content = content)
}

@Composable fun RoomMateApp() {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseDatabase.getInstance().reference }
    var uid by remember { mutableStateOf(auth.currentUser?.uid.orEmpty()) }
    var signedIn by remember { mutableStateOf(auth.currentUser != null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var linkEmail by remember { mutableStateOf("") }
    var linkPassword by remember { mutableStateOf("") }
    var linkConfirmPassword by remember { mutableStateOf("") }
    var showLinkPassword by remember { mutableStateOf(false) }
    var accountVersion by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var roomId by remember { mutableStateOf("") }
    var myName by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("My Room") }
    var item by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var message by remember { mutableStateOf("Connecting to your room…") }
    var expenses by remember { mutableStateOf(listOf<Expense>()) }
    var members by remember { mutableStateOf(listOf<Member>()) }
    var isAdmin by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Home") }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var restoring by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("room_session", Context.MODE_PRIVATE) }
    var memberListener by remember { mutableStateOf<ValueEventListener?>(null) }
    var expenseListener by remember { mutableStateOf<ValueEventListener?>(null) }
    var listeningRoom by remember { mutableStateOf("") }

    fun clearRoomSessionFor(userId: String) {
        if (userId.isBlank()) return
        prefs.edit()
            .remove("roomId_$userId")
            .remove("myName_$userId")
            .remove("whatsapp_$userId")
            .remove("invite_$userId")
            .apply()
    }

    fun clearRoomSession() = clearRoomSessionFor(uid)

    fun saveRoomSession() {
        if (uid.isBlank() || roomId.isBlank()) return
        prefs.edit()
            .putString("roomId_$uid", roomId)
            .putString("myName_$uid", myName)
            .putString("whatsapp_$uid", whatsapp)
            .putString("invite_$uid", invite)
            .apply()
    }

    fun detachRoomListeners() {
        if (listeningRoom.isNotBlank()) {
            memberListener?.let { db.child("rooms").child(listeningRoom).child("members").removeEventListener(it) }
            expenseListener?.let { db.child("rooms").child(listeningRoom).child("expenses").removeEventListener(it) }
        }
        listeningRoom = ""
        memberListener = null
        expenseListener = null
    }

    fun watchRoom(id: String) {
        detachRoomListeners()
        listeningRoom = id
        roomId = id
        saveRoomSession()
        db.child("rooms").child(id).child("info").child("name").get().addOnSuccessListener { roomName = it.getValue(String::class.java) ?: "My Room" }
        memberListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { members = s.children.mapNotNull { it.getValue(Member::class.java) }; isAdmin = s.child(uid).child("role").getValue(String::class.java) == "admin" }
            override fun onCancelled(e: DatabaseError) { message = e.message }
        }
        expenseListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { expenses = s.children.mapNotNull { it.getValue(Expense::class.java) }.sortedByDescending { it.date } }
            override fun onCancelled(e: DatabaseError) { message = e.message }
        }
        db.child("rooms").child(id).child("members").addValueEventListener(memberListener!!)
        db.child("rooms").child(id).child("expenses").addValueEventListener(expenseListener!!)
    }

    DisposableEffect(Unit) {
        onDispose { detachRoomListeners() }
    }

    fun restoreRoom(user: FirebaseUser, signedInMessage: String = "You're connected") {
        uid = user.uid
        signedIn = true
        val savedRoom = prefs.getString("roomId_${user.uid}", "").orEmpty()
        restoring = savedRoom.isNotBlank()
        if (savedRoom.isBlank()) {
            message = signedInMessage
            return
        }
        db.child("rooms").child(savedRoom).child("members").child(user.uid).get()
            .addOnSuccessListener { snap ->
                restoring = false
                if (!snap.exists()) {
                    clearRoomSessionFor(user.uid)
                    message = signedInMessage
                    return@addOnSuccessListener
                }
                val member = snap.getValue(Member::class.java)
                myName = member?.name?.takeIf { it.isNotBlank() } ?: prefs.getString("myName_${user.uid}", "").orEmpty()
                whatsapp = member?.whatsapp?.takeIf { it.isNotBlank() } ?: prefs.getString("whatsapp_${user.uid}", "").orEmpty()
                invite = prefs.getString("invite_${user.uid}", "").orEmpty()
                watchRoom(savedRoom)
                message = if (signedInMessage == "You're connected") "Room restored" else signedInMessage
            }
            .addOnFailureListener {
                restoring = false
                clearRoomSessionFor(user.uid)
                message = "Could not restore room — please join again"
            }
    }

    LaunchedEffect(Unit) {
        val user = auth.currentUser
        if (user != null) {
            restoreRoom(user)
        } else {
            signedIn = false
            restoring = false
            message = "Sign in to continue"
        }
    }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { a ->
            val user = a.currentUser
            if (user == null) {
                signedIn = false
                uid = ""
                restoring = false
                detachRoomListeners()
                roomId = ""
                invite = ""
                expenses = emptyList()
                members = emptyList()
                tab = "Home"
            } else {
                uid = user.uid
                signedIn = true
            }
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    fun validateSignIn(): Boolean {
        if (email.isBlank() || password.isBlank()) { message = "Enter email and password"; return false }
        if (!isValidEmail(email)) { message = "Enter a valid email address"; return false }
        return true
    }

    fun validateSignUp(): Boolean {
        if (!isValidEmail(email)) { message = "Enter a valid email address"; return false }
        passwordIssue(password)?.let { message = it; return false }
        if (password != confirmPassword) { message = "Passwords do not match"; return false }
        return true
    }

    fun signIn() {
        if (!validateSignIn()) return
        busy = true
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                busy = false
                val user = result.user ?: return@addOnSuccessListener
                restoreRoom(user)
            }
            .addOnFailureListener { busy = false; message = friendlyAuthError(it) }
    }

    fun signUp() {
        if (!validateSignUp()) return
        busy = true
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                busy = false
                val user = result.user ?: return@addOnSuccessListener
                uid = user.uid
                signedIn = true
                restoring = false
                message = "Account created — you're connected"
            }
            .addOnFailureListener { busy = false; message = friendlyAuthError(it) }
    }

    fun submitAuth() {
        if (isSignUp) signUp() else signIn()
    }

    fun sendPasswordReset() {
        if (!isValidEmail(email)) { message = "Enter a valid email address to reset your password"; return }
        busy = true
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { busy = false; message = "Password reset email sent to ${email.trim()}" }
            .addOnFailureListener { error ->
                busy = false
                message = if (error is FirebaseAuthInvalidUserException) "If an account exists, a reset link was sent." else friendlyAuthError(error)
            }
    }

    fun sendVerificationEmail(user: FirebaseUser) {
        user.sendEmailVerification()
            .addOnSuccessListener { message = "Verification email sent to ${user.email.orEmpty()}" }
            .addOnFailureListener { message = friendlyAuthError(it) }
    }

    fun refreshAccount() {
        val user = auth.currentUser ?: return
        user.reload().addOnSuccessListener {
            accountVersion++
            message = if (user.isEmailVerified) "Email verified" else "Email not verified yet"
        }.addOnFailureListener { message = friendlyAuthError(it) }
    }

    fun clearAuthFields() {
        email = ""
        password = ""
        confirmPassword = ""
        isSignUp = false
        showPassword = false
        showConfirmPassword = false
        linkEmail = ""
        linkPassword = ""
        linkConfirmPassword = ""
        showLinkPassword = false
    }

    fun createRoom() {
        if (uid.isBlank() || myName.isBlank() || whatsapp.isBlank() || roomName.isBlank()) { message = "Please complete all fields"; return }
        val code = (100000..999999).random().toString(); val id = db.child("rooms").push().key ?: return
        val member = Member(uid, myName.trim(), whatsapp.trim(), "admin")
        db.child("rooms").child(id).child("info").setValue(mapOf("name" to roomName.trim(), "createdBy" to uid, "createdAt" to ServerValue.TIMESTAMP)).addOnSuccessListener {
            db.child("rooms").child(id).child("members").child(uid).setValue(member).addOnSuccessListener {
                db.child("inviteCodes").child(code).setValue(id).addOnSuccessListener { invite = code; watchRoom(id); message = "Room created successfully" }
                    .addOnFailureListener { message = it.message ?: "Could not create invite code" }
            }.addOnFailureListener { message = it.message ?: "Could not add admin" }
        }.addOnFailureListener { message = it.message ?: "Could not create room" }
    }
    fun joinRoom() {
        if (uid.isBlank() || myName.isBlank() || whatsapp.isBlank() || invite.length != 6) { message = "Enter your name, WhatsApp number and 6-digit code"; return }
        db.child("inviteCodes").child(invite).get().addOnSuccessListener { snap ->
            val id = snap.getValue(String::class.java)
            if (id.isNullOrBlank()) { message = "That invite code was not found"; return@addOnSuccessListener }
            db.child("rooms").child(id).child("members").child(uid).setValue(Member(uid, myName.trim(), whatsapp.trim(), "member")).addOnSuccessListener { watchRoom(id); message = "Welcome to the room!" }.addOnFailureListener { message = it.message ?: "Could not join room" }
        }.addOnFailureListener { message = it.message ?: "Could not check invite code" }
    }
    fun saveExpense() {
        val v = amount.toDoubleOrNull()
        if (item.isBlank() || v == null || v <= 0.0) { message = "Enter an item and a valid amount"; return }
        val ref = db.child("rooms").child(roomId).child("expenses")
        val old = editing
        val id = old?.id ?: ref.push().key ?: return
        val record = Expense(id, old?.memberUid ?: uid, old?.memberName ?: myName, item.trim(), v, category, old?.date ?: System.currentTimeMillis())
        ref.child(id).setValue(record).addOnSuccessListener { item = ""; amount = ""; editing = null; message = if (old == null) "Expense added for everyone" else "Expense updated" }.addOnFailureListener { message = it.message ?: "Could not save expense" }
    }

    val cal = Calendar.getInstance(); val monthExpenses = expenses.filter { val c = Calendar.getInstance().apply { timeInMillis = it.date }; c.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && c.get(Calendar.MONTH) == cal.get(Calendar.MONTH) }
    val total = monthExpenses.sumOf { it.amount }; val personalTotal = monthExpenses.filter { it.memberUid == uid }.sumOf { it.amount }
    fun money(n: Double) = "₹" + String.format(Locale("en", "IN"), "%,.2f", n)

    if (restoring) {
        Column(Modifier.fillMaxSize().background(Pale).padding(22.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RoomMate", color = Purple, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("EXPENSES", color = Purple, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = Purple)
            Spacer(Modifier.height(12.dp))
            Text("Restoring your room…", color = Purple, style = MaterialTheme.typography.bodyLarge)
        }
    } else if (!signedIn) {
        LoginScreen(
            email = email,
            onEmailChange = { email = it },
            password = password,
            onPasswordChange = { password = it },
            confirmPassword = confirmPassword,
            onConfirmPasswordChange = { confirmPassword = it },
            isSignUp = isSignUp,
            onModeChange = { isSignUp = it; message = "" },
            showPassword = showPassword,
            onToggleShowPassword = { showPassword = !showPassword },
            showConfirmPassword = showConfirmPassword,
            onToggleConfirmPassword = { showConfirmPassword = !showConfirmPassword },
            message = message,
            busy = busy,
            onSubmit = { submitAuth() },
            onForgotPassword = { sendPasswordReset() }
        )
    } else if (roomId.isBlank()) {
        Column(Modifier.fillMaxSize().background(Pale).padding(22.dp), verticalArrangement = Arrangement.Center) {
            Text("RoomMate", color = Purple, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("EXPENSES", color = Purple, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp)); Text("Shared spending, made simple.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(3.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Get started", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(roomName, { roomName = it }, label = { Text("Room name (for new room)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(myName, { myName = it }, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(whatsapp, { whatsapp = it.filter { c -> c.isDigit() || c == '+' }.take(15) }, label = { Text("WhatsApp number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(invite, { invite = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit room code (to join)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { createRoom() }, enabled = uid.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Create a new room") }
                    OutlinedButton(onClick = { joinRoom() }, enabled = uid.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Join with invite code") }
                }
            }
            Spacer(Modifier.height(12.dp)); Text(message, color = Purple, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                clearRoomSession()
                detachRoomListeners()
                auth.signOut()
                signedIn = false
                restoring = false
                clearAuthFields()
                message = "Signed out"
            }) { Text("Sign out") }
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color(0xFFFAF9FC))) {
            Column(Modifier.fillMaxWidth().background(Purple).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp)) {
                Text("ROOMMATE EXPENSES", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(roomName, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Invite code  $invite", color = Color.White); Spacer(Modifier.weight(1f)); Text("${members.size} members", color = Color.White) }
            }
            when (tab) {
                "Home" -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Card(colors = CardDefaults.cardColors(containerColor = Pale), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) { Text("ROOM SPENDING · THIS MONTH", color = Purple, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(money(total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Purple); Text("${monthExpenses.size} expense entries", color = Color.DarkGray) } } }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SummaryTile("Your spending", money(personalTotal), Modifier.weight(1f)); SummaryTile("Members", members.size.toString(), Modifier.weight(1f)) } }
                    item { Text("Recent expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(expenses.take(5), key = { it.id }) { e -> ExpenseRow(e, ::money, onEdit = { if (e.memberUid == uid || isAdmin) { editing = e; item = e.item; amount = e.amount.toString(); category = e.category; tab = "Expenses" } }, onDelete = { if (e.memberUid == uid || isAdmin) db.child("rooms").child(roomId).child("expenses").child(e.id).removeValue().addOnSuccessListener { message = "Expense deleted" } }) }
                    if (expenses.isEmpty()) item { EmptyState("No expenses yet", "Add your first shared expense to get started.") }
                }
                "Expenses" -> Column(Modifier.weight(1f).padding(16.dp)) {
                    Text(if (editing == null) "Add an expense" else "Edit expense", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(item, { item = it }, label = { Text("What did you spend on?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Category", modifier = Modifier.padding(top = 12.dp, bottom = 6.dp), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Food", "Rent", "Bills", "Travel").forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Shopping", "Other").forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) } }
                    Button(onClick = { saveExpense() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(50.dp)) { Text(if (editing == null) "Save expense" else "Update expense") }
                    if (editing != null) TextButton(onClick = { editing = null; item = ""; amount = "" }) { Text("Cancel editing") }
                    Spacer(Modifier.height(12.dp)); Text("All expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LazyColumn(Modifier.weight(1f)) { items(expenses, key = { it.id }) { e -> ExpenseRow(e, ::money, onEdit = { if (e.memberUid == uid || isAdmin) { editing = e; item = e.item; amount = e.amount.toString(); category = e.category } }, onDelete = { if (e.memberUid == uid || isAdmin) db.child("rooms").child(roomId).child("expenses").child(e.id).removeValue() }) } }
                }
                "Members" -> LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item { Text("Room members", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Everyone in this room can view shared expenses.", color = Color.Gray) }
                    items(members, key = { it.uid }) { m -> Card(shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(m.name + if (m.uid == uid) " (You)" else "", fontWeight = FontWeight.SemiBold); Text(m.whatsapp, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }; if (m.role == "admin") AssistChip(onClick = {}, label = { Text("Admin") }) else if (isAdmin) TextButton(onClick = { db.child("rooms").child(roomId).child("members").child(m.uid).removeValue().addOnSuccessListener { message = "Member removed" } }) { Text("Remove", color = Color.Red) } } } }
                }
                else -> Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (message.isNotBlank()) Text(message, color = Purple, style = MaterialTheme.typography.bodySmall)
                    Card { Column(Modifier.padding(16.dp)) { Text(myName, style = MaterialTheme.typography.titleLarge); Text(whatsapp, color = Color.Gray); Text(if (isAdmin) "Room admin" else "Room member", color = Purple) } }
                    val user = auth.currentUser
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(user?.email ?: "Guest / anonymous account", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            if (user != null && !user.isAnonymous) {
                                key(accountVersion) {
                                    Text(if (user.isEmailVerified) "Email verified" else "Email not verified", color = if (user.isEmailVerified) Purple else Color.Red, style = MaterialTheme.typography.bodySmall)
                                    if (!user.isEmailVerified) {
                                        TextButton(onClick = { sendVerificationEmail(user) }, enabled = !busy) { Text("Send verification email") }
                                        TextButton(onClick = { refreshAccount() }, enabled = !busy) { Text("Refresh verification status") }
                                    }
                                }
                            }
                            if (user != null && user.isAnonymous) {
                                OutlinedTextField(linkEmail, { linkEmail = it }, label = { Text("Email for permanent account") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(
                                    linkPassword,
                                    { linkPassword = it },
                                    label = { Text("Choose a password") },
                                    visualTransformation = if (showLinkPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = { TextButton(onClick = { showLinkPassword = !showLinkPassword }) { Text(if (showLinkPassword) "Hide" else "Show") } },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    linkConfirmPassword,
                                    { linkConfirmPassword = it },
                                    label = { Text("Confirm password") },
                                    visualTransformation = if (showLinkPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                Button(onClick = {
                                    if (!isValidEmail(linkEmail)) { message = "Enter a valid email address"; return@Button }
                                    passwordIssue(linkPassword)?.let { message = it; return@Button }
                                    if (linkPassword != linkConfirmPassword) { message = "Passwords do not match"; return@Button }
                                    busy = true
                                    user.linkWithCredential(EmailAuthProvider.getCredential(linkEmail.trim(), linkPassword))
                                        .addOnSuccessListener { busy = false; message = "Account saved permanently"; linkEmail = ""; linkPassword = ""; linkConfirmPassword = "" }
                                        .addOnFailureListener { error -> busy = false; message = friendlyAuthError(error) }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Make account permanent") }
                            }
                            TextButton(onClick = {
                                clearRoomSession()
                                detachRoomListeners()
                                auth.signOut()
                                signedIn = false
                                restoring = false
                                roomId = ""
                                invite = ""
                                expenses = emptyList()
                                members = emptyList()
                                tab = "Home"
                                clearAuthFields()
                                message = "Signed out"
                            }) { Text("Sign out") }
                        }
                    }
                    Text("Your total this month", color = Color.Gray); Text(money(personalTotal), style = MaterialTheme.typography.headlineMedium, color = Purple, fontWeight = FontWeight.Bold)
                    Text("Room code: $invite")
                    Text("RoomMate Expenses · INR (₹)", color = Color.Gray)
                    TextButton(onClick = { clearRoomSession(); detachRoomListeners(); roomId = ""; invite = ""; expenses = emptyList(); members = emptyList(); tab = "Home"; message = "Left room on this device" }) { Text("Leave this room on this device") }
                }
            }
            NavigationBar(containerColor = Color.White) { listOf("Home", "Expenses", "Members", "Profile").forEach { t -> NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Text(when (t) { "Home" -> "⌂"; "Expenses" -> "₹"; "Members" -> "♙"; else -> "●" }, color = if (tab == t) Purple else Color.Gray) }, label = { Text(t) }) } }
        }
    }
}

@Composable private fun LoginScreen(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    isSignUp: Boolean,
    onModeChange: (Boolean) -> Unit,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit,
    showConfirmPassword: Boolean,
    onToggleConfirmPassword: () -> Unit,
    message: String,
    busy: Boolean,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Pale).padding(22.dp), verticalArrangement = Arrangement.Center) {
        Text("RoomMate", color = Purple, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text("EXPENSES", color = Purple, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (isSignUp) "Create an account to protect your expenses." else "Sign in to protect your account and expenses.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(email, onEmailChange, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                OutlinedTextField(
                    password,
                    onPasswordChange,
                    label = { Text("Password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = onToggleShowPassword, enabled = !busy) { Text(if (showPassword) "Hide" else "Show") } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy
                )
                if (isSignUp) {
                    OutlinedTextField(
                        confirmPassword,
                        onConfirmPasswordChange,
                        label = { Text("Confirm password") },
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = { TextButton(onClick = onToggleConfirmPassword, enabled = !busy) { Text(if (showConfirmPassword) "Hide" else "Show") } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !busy
                    )
                }
                Button(onClick = onSubmit, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (busy) "Please wait..." else if (isSignUp) "Create account" else "Sign in") }
                TextButton(onClick = { onModeChange(!isSignUp) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (isSignUp) "Already have an account? Sign in" else "New here? Create an account") }
                if (!isSignUp) TextButton(onClick = onForgotPassword, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Forgot password?") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(message, color = Purple, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun SummaryTile(title: String, value: String, modifier: Modifier = Modifier) { Card(modifier, shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp)) { Text(title, color = Color.Gray, style = MaterialTheme.typography.labelMedium); Text(value, color = Purple, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } } }

@Composable private fun ExpenseRow(e: Expense, money: (Double) -> String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(vertical = 4.dp)) { Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(e.item, fontWeight = FontWeight.SemiBold); Text("${e.memberName} · ${e.category}", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }; Text(money(e.amount), color = Purple, fontWeight = FontWeight.Bold) }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onEdit) { Text("Edit") }; TextButton(onClick = onDelete) { Text("Delete", color = Color.Red) } } } }
}

@Composable private fun EmptyState(title: String, body: String) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Bold); Text(body, color = Color.Gray, style = MaterialTheme.typography.bodySmall) } }
