package com.roommate.expenses

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

data class Member(val uid: String = "", val name: String = "", val whatsapp: String = "", val role: String = "member")
data class Expense(val id: String = "", val memberUid: String = "", val memberName: String = "", val item: String = "", val amount: Double = 0.0, val category: String = "Other", val date: Long = System.currentTimeMillis())

private val Purple = Color(0xFF6542B8)
private val Pale = Color(0xFFF4F0FC)

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
    var linkEmail by remember { mutableStateOf("") }
    var linkPassword by remember { mutableStateOf("") }
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

    LaunchedEffect(Unit) {
        val user = auth.currentUser
        if (user != null) {
            uid = user.uid
            signedIn = true
            message = "You're connected"
        } else {
            signedIn = false
            message = "Sign in to continue"
        }
    }

    fun signIn() {
        if (email.isBlank() || password.isBlank()) { message = "Enter email and password"; return }
        busy = true
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { busy = false; uid = it.user?.uid.orEmpty(); signedIn = true; message = "You're connected" }
            .addOnFailureListener { busy = false; message = it.message ?: "Sign in failed" }
    }

    fun signUp() {
        if (email.isBlank() || password.isBlank()) { message = "Enter email and password"; return }
        busy = true
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { busy = false; uid = it.user?.uid.orEmpty(); signedIn = true; message = "Account created — you're connected" }
            .addOnFailureListener { busy = false; message = it.message ?: "Could not create account" }
    }

    fun watchRoom(id: String) {
        roomId = id
        db.child("rooms").child(id).child("info").child("name").get().addOnSuccessListener { roomName = it.getValue(String::class.java) ?: "My Room" }
        db.child("rooms").child(id).child("members").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { members = s.children.mapNotNull { it.getValue(Member::class.java) }; isAdmin = s.child(uid).child("role").getValue(String::class.java) == "admin" }
            override fun onCancelled(e: DatabaseError) { message = e.message }
        })
        db.child("rooms").child(id).child("expenses").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { expenses = s.children.mapNotNull { it.getValue(Expense::class.java) }.sortedByDescending { it.date } }
            override fun onCancelled(e: DatabaseError) { message = e.message }
        })
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

    if (!signedIn) {
        LoginScreen(email, { email = it }, password, { password = it }, message, busy, { signIn() }, { signUp() })
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
                auth.signOut()
                signedIn = false
                email = ""
                password = ""
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
                    Card { Column(Modifier.padding(16.dp)) { Text(myName, style = MaterialTheme.typography.titleLarge); Text(whatsapp, color = Color.Gray); Text(if (isAdmin) "Room admin" else "Room member", color = Purple) } }
                    val user = auth.currentUser
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(user?.email ?: "Guest / anonymous account", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            if (user != null && user.isAnonymous) {
                                OutlinedTextField(linkEmail, { linkEmail = it }, label = { Text("Email for permanent account") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                OutlinedTextField(linkPassword, { linkPassword = it }, label = { Text("Choose a password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Button(onClick = {
                                    if (linkEmail.isBlank() || linkPassword.isBlank()) { message = "Enter email and password"; return@Button }
                                    busy = true
                                    user.linkWithCredential(EmailAuthProvider.getCredential(linkEmail.trim(), linkPassword))
                                        .addOnSuccessListener { busy = false; message = "Account saved permanently"; linkEmail = ""; linkPassword = "" }
                                        .addOnFailureListener { busy = false; message = it.message ?: "Could not save account" }
                                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Make account permanent") }
                            }
                            TextButton(onClick = {
                                auth.signOut()
                                signedIn = false
                                roomId = ""
                                invite = ""
                                expenses = emptyList()
                                members = emptyList()
                                tab = "Home"
                                message = "Signed out"
                            }) { Text("Sign out") }
                        }
                    }
                    Text("Your total this month", color = Color.Gray); Text(money(personalTotal), style = MaterialTheme.typography.headlineMedium, color = Purple, fontWeight = FontWeight.Bold)
                    Text("Room code: $invite")
                    Text("RoomMate Expenses · INR (₹)", color = Color.Gray)
                    TextButton(onClick = { roomId = ""; invite = ""; expenses = emptyList(); members = emptyList(); tab = "Home" }) { Text("Leave this room on this device") }
                    Text(message, color = Purple, style = MaterialTheme.typography.bodySmall)
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
    message: String,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignUp: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Pale).padding(22.dp), verticalArrangement = Arrangement.Center) {
        Text("RoomMate", color = Purple, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text("EXPENSES", color = Purple, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Sign in to protect your account and expenses.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(email, onEmailChange, label = { Text("Email") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(password, onPasswordChange, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Sign in") }
                OutlinedButton(onClick = onSignUp, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Create account") }
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
