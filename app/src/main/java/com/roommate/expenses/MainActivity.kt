package com.roommate.expenses

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.DatePicker
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.json.JSONArray
import org.json.JSONObject

data class Member(val uid: String = "", val name: String = "", val whatsapp: String = "", val role: String = "member")
data class Expense(val id: String = "", val memberUid: String = "", val memberName: String = "", val item: String = "", val amount: Double = 0.0, val category: String = "Other", val date: Long = System.currentTimeMillis())
data class Note(val id: String = "", val text: String = "", val authorUid: String = "", val authorName: String = "", val date: Long = System.currentTimeMillis())
data class SavedRoom(val id: String = "", val name: String = "", val invite: String = "")

private val Purple = Color(0xFF6542B8)
private val Pale = Color(0xFFF4F0FC)
private val EmailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

private fun isValidEmail(value: String) = EmailPattern.matches(value.trim())

private fun isValidPhone(value: String) = value.trim().count { it.isDigit() } in 10..15

private fun startOfDay(value: Long): Long = Calendar.getInstance().apply {
    timeInMillis = value
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfDay(value: Long): Long = Calendar.getInstance().apply {
    timeInMillis = value
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

private fun dateLabel(value: Long): String = SimpleDateFormat("dd MMM yyyy", Locale("en", "IN")).format(Date(value))

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
    var signupName by remember { mutableStateOf("") }
    var signupPhone by remember { mutableStateOf("") }
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
    var personalMode by remember { mutableStateOf(false) }
    var expenseDate by remember { mutableStateOf(System.currentTimeMillis()) }
    var nameFilter by remember { mutableStateOf("") }
    var dateFrom by remember { mutableStateOf<Long?>(null) }
    var dateTo by remember { mutableStateOf<Long?>(null) }
    var notes by remember { mutableStateOf(listOf<Note>()) }
    var noteText by remember { mutableStateOf("") }
    var savedRooms by remember { mutableStateOf(listOf<SavedRoom>()) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var memberToRemove by remember { mutableStateOf<Member?>(null) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("room_session", Context.MODE_PRIVATE) }

    fun savedRoomsKey() = "saved_rooms_$uid"

    fun loadSavedRooms() {
        if (uid.isBlank()) { savedRooms = emptyList(); return }
        val raw = prefs.getString(savedRoomsKey(), "").orEmpty()
        if (raw.isBlank()) { savedRooms = emptyList(); return }
        try {
            val arr = JSONArray(raw)
            savedRooms = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SavedRoom(o.optString("id"), o.optString("name"), o.optString("invite"))
            }.filter { it.id.isNotBlank() }
        } catch (_: Exception) {
            savedRooms = emptyList()
        }
    }

    fun persistSavedRooms(rooms: List<SavedRoom>) {
        if (uid.isBlank()) return
        val arr = JSONArray()
        rooms.forEach { r -> arr.put(JSONObject().put("id", r.id).put("name", r.name).put("invite", r.invite)) }
        prefs.edit().putString(savedRoomsKey(), arr.toString()).apply()
        savedRooms = rooms
    }

    fun saveRoomLocally(id: String, name: String, code: String) {
        if (uid.isBlank() || id.isBlank()) return
        val updated = savedRooms.toMutableList()
        val index = updated.indexOfFirst { it.id == id }
        val entry = SavedRoom(id, name.ifBlank { "My Room" }, code)
        if (index >= 0) updated[index] = entry else updated.add(entry)
        persistSavedRooms(updated)
    }

    fun removeSavedRoom(id: String) = persistSavedRooms(savedRooms.filterNot { it.id == id })

    var memberListener by remember { mutableStateOf<ValueEventListener?>(null) }
    var expenseListener by remember { mutableStateOf<ValueEventListener?>(null) }
    var personalListener by remember { mutableStateOf<ValueEventListener?>(null) }
    var noteListener by remember { mutableStateOf<ValueEventListener?>(null) }
    var listeningRoom by remember { mutableStateOf("") }
    var listeningPersonal by remember { mutableStateOf(false) }
    var listeningNotes by remember { mutableStateOf("") }

    fun clearSessionFor(userId: String) {
        if (userId.isBlank()) return
        prefs.edit()
            .remove("roomId_$userId")
            .remove("myName_$userId")
            .remove("whatsapp_$userId")
            .remove("invite_$userId")
            .remove("personal_$userId")
            .apply()
    }

    fun clearRoomSession() = clearSessionFor(uid)

    fun saveRoomSession() {
        if (uid.isBlank() || roomId.isBlank()) return
        prefs.edit()
            .putString("roomId_$uid", roomId)
            .putString("myName_$uid", myName)
            .putString("whatsapp_$uid", whatsapp)
            .putString("invite_$uid", invite)
            .putBoolean("personal_$uid", false)
            .apply()
    }

    fun savePersonalMode() {
        if (uid.isBlank()) return
        prefs.edit().putBoolean("personal_$uid", true).apply()
    }

    fun loadProfile(onLoaded: () -> Unit = {}) {
        if (uid.isBlank()) { onLoaded(); return }
        db.child("users").child(uid).child("profile").get()
            .addOnSuccessListener { snap ->
                val savedName = snap.child("name").getValue(String::class.java).orEmpty()
                val savedPhone = snap.child("phone").getValue(String::class.java).orEmpty()
                myName = savedName.ifBlank { prefs.getString("myName_$uid", "").orEmpty() }
                whatsapp = savedPhone.ifBlank { prefs.getString("whatsapp_$uid", "").orEmpty() }
                if (myName.isBlank()) myName = auth.currentUser?.email?.substringBefore('@').orEmpty().ifBlank { "Member" }
                onLoaded()
            }
            .addOnFailureListener { onLoaded() }
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

    fun detachPersonalListener() {
        if (listeningPersonal) personalListener?.let { db.child("users").child(uid).child("personalExpenses").removeEventListener(it) }
        listeningPersonal = false
        personalListener = null
    }

    fun detachNotesListener() {
        if (listeningNotes.isNotBlank()) {
            noteListener?.let { listener ->
                if (listeningNotes == "personal") db.child("users").child(uid).child("personalNotes").removeEventListener(listener)
                else db.child("rooms").child(listeningNotes).child("notes").removeEventListener(listener)
            }
        }
        listeningNotes = ""
        noteListener = null
    }

    fun detachAllListeners() {
        detachRoomListeners()
        detachPersonalListener()
        detachNotesListener()
    }

    fun watchPersonal() {
        detachAllListeners()
        personalMode = true
        roomId = ""
        invite = ""
        myName = ""
        whatsapp = ""
        members = emptyList()
        notes = emptyList()
        loadProfile()
        listeningPersonal = true
        personalListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { expenses = s.children.mapNotNull { it.getValue(Expense::class.java) }.sortedByDescending { it.date } }
            override fun onCancelled(e: DatabaseError) { message = e.message }
        }
        db.child("users").child(uid).child("personalExpenses").addValueEventListener(personalListener!!)
        listeningNotes = "personal"
        noteListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { notes = s.children.mapNotNull { it.getValue(Note::class.java) }.sortedByDescending { it.date } }
            override fun onCancelled(e: DatabaseError) { message = e.message }
        }
        db.child("users").child(uid).child("personalNotes").addValueEventListener(noteListener!!)
    }

    fun enterPersonalMode() {
        detachAllListeners()
        clearSessionFor(uid)
        savePersonalMode()
        expenses = emptyList()
        roomName = "Personal"
        tab = "Home"
        watchPersonal()
        message = "Personal mode — add expenses just for you"
    }

    fun watchRoom(id: String) {
        detachAllListeners()
        personalMode = false
        listeningRoom = id
        roomId = id
        notes = emptyList()
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
        listeningNotes = id
        noteListener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) { notes = s.children.mapNotNull { it.getValue(Note::class.java) }.sortedByDescending { it.date } }
            override fun onCancelled(e: DatabaseError) { message = e.message }
        }
        db.child("rooms").child(id).child("notes").addValueEventListener(noteListener!!)
    }

    fun openSavedRoom(room: SavedRoom) {
        if (uid.isBlank()) return
        db.child("rooms").child(room.id).child("members").child(uid).get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    removeSavedRoom(room.id)
                    message = "You are no longer a member of ${room.name}"
                    return@addOnSuccessListener
                }
                val member = snap.getValue(Member::class.java)
                myName = member?.name?.takeIf { it.isNotBlank() } ?: myName
                whatsapp = member?.whatsapp ?: whatsapp
                invite = room.invite
                watchRoom(room.id)
                message = "Opened ${room.name}"
            }
            .addOnFailureListener { message = it.message ?: "Could not open saved room" }
    }

    DisposableEffect(Unit) {
        onDispose { detachAllListeners() }
    }

    fun restoreRoom(user: FirebaseUser, signedInMessage: String = "You're connected") {
        uid = user.uid
        signedIn = true
        loadSavedRooms()
        loadProfile()
        val savedRoom = prefs.getString("roomId_${user.uid}", "").orEmpty()
        restoring = savedRoom.isNotBlank()
        if (savedRoom.isBlank()) {
            personalMode = prefs.getBoolean("personal_${user.uid}", false)
            restoring = false
            if (personalMode) {
                roomName = "Personal"
                watchPersonal()
                message = "Personal mode"
            } else {
                message = signedInMessage
            }
            return
        }
        personalMode = false
        db.child("rooms").child(savedRoom).child("members").child(user.uid).get()
            .addOnSuccessListener { snap ->
                restoring = false
                if (!snap.exists()) {
                    clearSessionFor(user.uid)
                    personalMode = false
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
                clearSessionFor(user.uid)
                personalMode = false
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
                personalMode = false
                detachAllListeners()
                roomId = ""
                invite = ""
                expenses = emptyList()
                members = emptyList()
                tab = "Home"
            } else {
                uid = user.uid
                signedIn = true
                loadSavedRooms()
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
        if (signupName.isBlank()) { message = "Enter your name"; return false }
        if (!isValidPhone(signupPhone)) { message = "Enter a valid 10-15 digit mobile number"; return false }
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
                val user = result.user ?: return@addOnSuccessListener
                uid = user.uid
                signedIn = true
                restoring = false
                personalMode = false
                val name = signupName.trim()
                val phone = signupPhone.trim()
                val profile = mapOf("name" to name, "phone" to phone, "email" to (user.email.orEmpty()))
                db.child("users").child(user.uid).child("profile").setValue(profile)
                    .addOnSuccessListener {
                        myName = name
                        whatsapp = phone
                        signupName = ""
                        signupPhone = ""
                        busy = false
                        message = "Account created — you're connected"
                    }
                    .addOnFailureListener {
                        myName = name
                        whatsapp = phone
                        signupName = ""
                        signupPhone = ""
                        busy = false
                        message = "Account created, but profile could not be saved"
                    }
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
        signupName = ""
        signupPhone = ""
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
        if (uid.isBlank() || roomName.isBlank()) { message = "Enter a room name"; return }
        loadProfile {
            val name = roomName.trim()
            val code = (100000..999999).random().toString(); val id = db.child("rooms").push().key ?: return@loadProfile
            val member = Member(uid, myName.trim().ifBlank { "Member" }, whatsapp.trim(), "admin")
            db.child("rooms").child(id).child("info").setValue(mapOf("name" to name, "createdBy" to uid, "createdAt" to ServerValue.TIMESTAMP)).addOnSuccessListener {
                db.child("rooms").child(id).child("members").child(uid).setValue(member).addOnSuccessListener {
                    db.child("inviteCodes").child(code).setValue(id).addOnSuccessListener {
                        invite = code
                        saveRoomLocally(id, name, code)
                        watchRoom(id)
                        message = "Room created successfully"
                    }.addOnFailureListener { message = it.message ?: "Could not create invite code" }
                }.addOnFailureListener { message = it.message ?: "Could not add admin" }
            }.addOnFailureListener { message = it.message ?: "Could not create room" }
        }
    }

    fun joinRoom() {
        if (uid.isBlank() || invite.length != 6) { message = "Enter the 6-digit room code"; return }
        loadProfile {
            db.child("inviteCodes").child(invite).get().addOnSuccessListener { snap ->
                val id = snap.getValue(String::class.java)
                if (id.isNullOrBlank()) { message = "That invite code was not found"; return@addOnSuccessListener }
                val member = Member(uid, myName.trim().ifBlank { "Member" }, whatsapp.trim(), "member")
                db.child("rooms").child(id).child("members").child(uid).setValue(member).addOnSuccessListener {
                    db.child("rooms").child(id).child("info").child("name").get().addOnSuccessListener { nameSnap ->
                        val name = nameSnap.getValue(String::class.java) ?: "My Room"
                        saveRoomLocally(id, name, invite)
                        watchRoom(id)
                        message = "Welcome to $name!"
                    }.addOnFailureListener {
                        saveRoomLocally(id, "My Room", invite)
                        watchRoom(id)
                        message = "Welcome to the room!"
                    }
                }.addOnFailureListener { message = it.message ?: "Could not join room" }
            }.addOnFailureListener { message = it.message ?: "Could not check invite code" }
        }
    }
    fun openDatePicker(initial: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, day: Int ->
                val picked = Calendar.getInstance().apply {
                    set(year, month, day, 12, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onPicked(picked)
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
            show()
        }
    }

    fun expenseRef() = if (personalMode) db.child("users").child(uid).child("personalExpenses") else db.child("rooms").child(roomId).child("expenses")

    fun noteRef() = if (personalMode) db.child("users").child(uid).child("personalNotes") else db.child("rooms").child(roomId).child("notes")

    fun deleteExpense(e: Expense) {
        if (e.memberUid != uid) { message = "Only the person who added an expense can delete it"; expenseToDelete = null; return }
        expenseRef().child(e.id).removeValue()
            .addOnSuccessListener { message = "Expense deleted"; expenseToDelete = null }
            .addOnFailureListener { message = it.message ?: "Could not delete expense"; expenseToDelete = null }
    }

    fun removeMember(m: Member) {
        if (!isAdmin) { message = "Only the room admin can remove members"; memberToRemove = null; return }
        db.child("rooms").child(roomId).child("members").child(m.uid).removeValue()
            .addOnSuccessListener { message = "${m.name} removed from the room"; memberToRemove = null }
            .addOnFailureListener { message = it.message ?: "Could not remove member"; memberToRemove = null }
    }

    fun saveNote() {
        if (noteText.isBlank()) { message = "Enter a note"; return }
        val id = noteRef().push().key ?: return
        val authorName = if (personalMode) (myName.ifBlank { "You" }) else myName.ifBlank { "Member" }
        val note = Note(id, noteText.trim(), uid, authorName, System.currentTimeMillis())
        noteRef().child(id).setValue(note)
            .addOnSuccessListener { noteText = ""; message = if (personalMode) "Personal note saved" else "Room note saved" }
            .addOnFailureListener { message = it.message ?: "Could not save note" }
    }

    fun deleteNote(note: Note) {
        if (note.authorUid != uid) { message = "Only the author can delete a note"; noteToDelete = null; return }
        noteRef().child(note.id).removeValue()
            .addOnSuccessListener { message = "Note deleted"; noteToDelete = null }
            .addOnFailureListener { message = it.message ?: "Could not delete note"; noteToDelete = null }
    }

    fun saveExpense() {
        val v = amount.toDoubleOrNull()
        if (item.isBlank() || v == null || v <= 0.0) { message = "Enter an item and a valid amount"; return }
        val ref = expenseRef()
        val old = editing
        val id = old?.id ?: ref.push().key ?: return
        val memberName = old?.memberName ?: if (personalMode) "You" else myName
        val chosenDate = if (old != null) expenseDate else startOfDay(expenseDate)
        val record = Expense(id, old?.memberUid ?: uid, memberName, item.trim(), v, category, chosenDate)
        ref.child(id).setValue(record).addOnSuccessListener {
            item = ""; amount = ""; editing = null; expenseDate = System.currentTimeMillis()
            message = when {
                personalMode && old == null -> "Personal expense added"
                personalMode -> "Personal expense updated"
                old == null -> "Expense added for everyone"
                else -> "Expense updated"
            }
        }.addOnFailureListener { message = it.message ?: "Could not save expense" }
    }

    fun leavePersonalMode() {
        detachAllListeners()
        clearSessionFor(uid)
        personalMode = false
        roomId = ""
        invite = ""
        roomName = "My Room"
        expenses = emptyList()
        members = emptyList()
        tab = "Home"
        message = "Choose a room option"
    }

    val cal = Calendar.getInstance(); val monthExpenses = expenses.filter { val c = Calendar.getInstance().apply { timeInMillis = it.date }; c.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && c.get(Calendar.MONTH) == cal.get(Calendar.MONTH) }
    val total = monthExpenses.sumOf { it.amount }; val personalTotal = monthExpenses.filter { it.memberUid == uid }.sumOf { it.amount }
    val filteredExpenses = expenses.filter { e ->
        (nameFilter.isBlank() || e.item.contains(nameFilter.trim(), true)) &&
            (dateFrom == null || e.date >= startOfDay(dateFrom!!)) &&
            (dateTo == null || e.date <= endOfDay(dateTo!!))
    }
    fun money(n: Double) = "₹" + String.format(Locale("en", "IN"), "%,.2f", n)
    fun clearExpenseFilters() { nameFilter = ""; dateFrom = null; dateTo = null; message = "Filters cleared" }

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
            signupName = signupName,
            onSignupNameChange = { signupName = it },
            signupPhone = signupPhone,
            onSignupPhoneChange = { signupPhone = it.filter { c -> c.isDigit() || c == '+' }.take(15) },
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
    } else if (roomId.isBlank() && !personalMode) {
        Column(Modifier.fillMaxSize().background(Pale).padding(22.dp), verticalArrangement = Arrangement.Center) {
            Text("RoomMate", color = Purple, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("EXPENSES", color = Purple, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp)); Text("Shared spending, made simple.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(3.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Create a room", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(roomName, { roomName = it }, label = { Text("Room name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Button(onClick = { createRoom() }, enabled = uid.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Create a new room") }
                    Text("Join a room", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(invite, { invite = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit room code") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedButton(onClick = { joinRoom() }, enabled = uid.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Join with invite code") }
                    if (savedRooms.isNotEmpty()) {
                        Text("Your saved rooms", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        savedRooms.forEach { room ->
                            OutlinedButton(onClick = { openSavedRoom(room) }, modifier = Modifier.fillMaxWidth()) { Text("Open ${room.name}") }
                        }
                    }
                    TextButton(onClick = { enterPersonalMode() }, enabled = uid.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Continue without a room — personal expenses") }
                }
            }
            Spacer(Modifier.height(12.dp)); Text(message, color = Purple, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                clearRoomSession()
                detachAllListeners()
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
                Text(if (personalMode) "PERSONAL EXPENSES" else "ROOMMATE EXPENSES", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(if (personalMode) "Just for you" else roomName, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (personalMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Personal mode", color = Color.White); Spacer(Modifier.weight(1f)); Text("Private to your account", color = Color.White.copy(alpha = .8f)) }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Invite code  $invite", color = Color.White); Spacer(Modifier.weight(1f)); Text("${members.size} members", color = Color.White) }
                }
            }
            when (tab) {
                "Home" -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Card(colors = CardDefaults.cardColors(containerColor = Pale), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) { Text(if (personalMode) "YOUR SPENDING · THIS MONTH" else "ROOM SPENDING · THIS MONTH", color = Purple, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold); Text(money(total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Purple); Text("${monthExpenses.size} expense entries", color = Color.DarkGray) } } }
                    if (!personalMode) {
                        item { Card(shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp)) { Text("Spending by category · this month", fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); CategoryPieChart(monthExpenses.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { e -> e.amount } }, ::money) } } }
                    }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SummaryTile("Your spending", money(personalTotal), Modifier.weight(1f)); if (personalMode) SummaryTile("Entries", monthExpenses.size.toString(), Modifier.weight(1f)) else SummaryTile("Members", members.size.toString(), Modifier.weight(1f)) } }
                    item { Text("Recent expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(expenses.take(5), key = { it.id }) { e -> ExpenseRow(e, ::money, e.memberUid == uid, onEdit = { editing = e; item = e.item; amount = e.amount.toString(); category = e.category; expenseDate = e.date; tab = "Expenses" }, onDelete = { expenseToDelete = e }) }
                    if (expenses.isEmpty()) item { EmptyState("No expenses yet", if (personalMode) "Add your first personal expense to get started." else "Add your first shared expense to get started.") }
                }
                "Expenses" -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text(if (editing == null) "Add an expense" else "Edit expense", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item { OutlinedTextField(item, { item = it }, label = { Text("What did you spend on?") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item { OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(10) }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true) }
                    item {
                        Column {
                            Text("Expense date", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(onClick = { openDatePicker(expenseDate) { expenseDate = it } }, modifier = Modifier.fillMaxWidth()) { Text(dateLabel(expenseDate)) }
                            TextButton(onClick = { expenseDate = System.currentTimeMillis() }, modifier = Modifier.fillMaxWidth()) { Text("Use today") }
                        }
                    }
                    item {
                        Column {
                            Text("Category", modifier = Modifier.padding(bottom = 6.dp), fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Food", "Rent", "Bills", "Travel").forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) } }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Shopping", "Other").forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) } }
                        }
                    }
                    item { Button(onClick = { saveExpense() }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(if (editing == null) "Save expense" else "Update expense") } }
                    if (editing != null) item { TextButton(onClick = { editing = null; item = ""; amount = ""; expenseDate = System.currentTimeMillis() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel editing") } }
                    item {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Filter expenses", fontWeight = FontWeight.Bold)
                                OutlinedTextField(nameFilter, { nameFilter = it }, label = { Text("Search by expense name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { openDatePicker(dateFrom ?: System.currentTimeMillis()) { picked -> val day = startOfDay(picked); dateFrom = day; if (dateTo != null && day > dateTo!!) dateTo = null } }, modifier = Modifier.weight(1f)) { Text(dateFrom?.let { "From ${dateLabel(it)}" } ?: "From date") }
                                    OutlinedButton(onClick = { openDatePicker(dateTo ?: System.currentTimeMillis()) { picked -> val day = startOfDay(picked); if (dateFrom != null && day < dateFrom!!) dateFrom = null; dateTo = day } }, modifier = Modifier.weight(1f)) { Text(dateTo?.let { "To ${dateLabel(it)}" } ?: "To date") }
                                }
                                TextButton(onClick = { clearExpenseFilters() }, modifier = Modifier.fillMaxWidth()) { Text("Clear filters") }
                            }
                        }
                    }
                    item { Text("All expenses (${filteredExpenses.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(filteredExpenses, key = { it.id }) { e -> ExpenseRow(e, ::money, e.memberUid == uid, onEdit = { editing = e; item = e.item; amount = e.amount.toString(); category = e.category; expenseDate = e.date }, onDelete = { expenseToDelete = e }) }
                    if (filteredExpenses.isEmpty()) item { EmptyState("No matching expenses", "Try a different name or date range.") }
                }
                "Members" -> LazyColumn(Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item { Text("Room members", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Everyone in this room can view shared expenses.", color = Color.Gray) }
                    items(members, key = { it.uid }) { m -> Card(shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(m.name + if (m.uid == uid) " (You)" else "", fontWeight = FontWeight.SemiBold); Text(m.whatsapp, color = Color.Gray, style = MaterialTheme.typography.bodySmall) }; if (m.role == "admin") AssistChip(onClick = {}, label = { Text("Admin") }) else if (isAdmin) TextButton(onClick = { memberToRemove = m }) { Text("Remove", color = Color.Red) } } } }
                }
                "Notes" -> LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text("Notebook", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item { Text(if (personalMode) "Private notes for your account." else "Shared notes for everyone in this room.", color = Color.Gray) }
                    item { OutlinedTextField(noteText, { noteText = it }, label = { Text("Write a note") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                    item { Button(onClick = { saveNote() }, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Save note") } }
                    item { Text("Saved notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    items(notes, key = { it.id }) { n ->
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(n.text)
                                Text("${n.authorName} · ${dateLabel(n.date)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                if (n.authorUid == uid) TextButton(onClick = { noteToDelete = n }) { Text("Delete", color = Color.Red) }
                            }
                        }
                    }
                    if (notes.isEmpty()) item { EmptyState("No notes yet", "Write the first note to get started.") }
                }
                else -> Column(Modifier.weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (message.isNotBlank()) Text(message, color = Purple, style = MaterialTheme.typography.bodySmall)
                    Card { Column(Modifier.padding(16.dp)) { Text(if (myName.isBlank()) if (personalMode) "Personal account" else "Your profile" else myName, style = MaterialTheme.typography.titleLarge); if (whatsapp.isNotBlank()) Text(whatsapp, color = Color.Gray); Text(if (personalMode) "Personal mode" else if (isAdmin) "Room admin" else "Room member", color = Purple) } }
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
                            if (personalMode) {
                                TextButton(onClick = { leavePersonalMode() }, modifier = Modifier.fillMaxWidth()) { Text("Create or join a room") }
                            } else {
                                TextButton(onClick = { enterPersonalMode() }, modifier = Modifier.fillMaxWidth()) { Text("Use personal expenses instead") }
                            }
                            TextButton(onClick = {
                                clearRoomSession()
                                detachAllListeners()
                                personalMode = false
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
                    Text(if (personalMode) "Personal mode — no room or invite code" else "Room code: $invite")
                    Text("RoomMate Expenses · INR (₹)", color = Color.Gray)
                    if (!personalMode) TextButton(onClick = { clearRoomSession(); detachAllListeners(); roomId = ""; invite = ""; expenses = emptyList(); members = emptyList(); tab = "Home"; message = "Left room on this device" }) { Text("Leave this room on this device") }
                }
            }
            NavigationBar(containerColor = Color.White) { (if (personalMode) listOf("Home", "Expenses", "Notes", "Profile") else listOf("Home", "Expenses", "Members", "Notes", "Profile")).forEach { t -> NavigationBarItem(selected = tab == t, onClick = { tab = t }, icon = { Text(when (t) { "Home" -> "⌂"; "Expenses" -> "₹"; "Members" -> "♙"; "Notes" -> "N"; else -> "●" }, color = if (tab == t) Purple else Color.Gray) }, label = { Text(t) }) } }
        }

        expenseToDelete?.let { e ->
            AlertDialog(
                onDismissRequest = { expenseToDelete = null },
                title = { Text("Delete expense?") },
                text = { Text("Delete \"${e.item}\" of ${money(e.amount)}? This cannot be undone.") },
                confirmButton = { TextButton(onClick = { deleteExpense(e) }) { Text("Delete", color = Color.Red) } },
                dismissButton = { TextButton(onClick = { expenseToDelete = null }) { Text("Cancel") } }
            )
        }
        memberToRemove?.let { m ->
            AlertDialog(
                onDismissRequest = { memberToRemove = null },
                title = { Text("Remove member?") },
                text = { Text("Remove ${m.name} from this room? They will no longer be able to add expenses.") },
                confirmButton = { TextButton(onClick = { removeMember(m) }) { Text("Remove", color = Color.Red) } },
                dismissButton = { TextButton(onClick = { memberToRemove = null }) { Text("Cancel") } }
            )
        }
        noteToDelete?.let { n ->
            AlertDialog(
                onDismissRequest = { noteToDelete = null },
                title = { Text("Delete note?") },
                text = { Text("This note will be permanently removed.") },
                confirmButton = { TextButton(onClick = { deleteNote(n) }) { Text("Delete", color = Color.Red) } },
                dismissButton = { TextButton(onClick = { noteToDelete = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable private fun LoginScreen(
    signupName: String,
    onSignupNameChange: (String) -> Unit,
    signupPhone: String,
    onSignupPhoneChange: (String) -> Unit,
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
    Column(Modifier.fillMaxSize().background(Pale).padding(22.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text("RoomMate", color = Purple, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text("EXPENSES", color = Purple, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (isSignUp) "Create an account to protect your expenses." else "Sign in to protect your account and expenses.", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isSignUp) {
                    OutlinedTextField(signupName, onSignupNameChange, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                    OutlinedTextField(signupPhone, onSignupPhoneChange, label = { Text("Mobile number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
                }
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

private val PieColors = listOf(
    Color(0xFF6542B8), Color(0xFF42A5F5), Color(0xFF26A69A),
    Color(0xFFEF6C00), Color(0xFFEC407A), Color(0xFF7E57C2)
)

@Composable private fun CategoryPieChart(totals: Map<String, Double>, money: (Double) -> String) {
    val entries = totals.filter { it.value > 0 }.toList().sortedByDescending { it.second }
    val sum = entries.sumOf { it.second }
    if (sum <= 0.0) {
        Text("No expenses this month yet", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(132.dp)) {
            var start = -90f
            entries.forEachIndexed { index, entry ->
                val sweep = 360f * (entry.second / sum).toFloat()
                drawArc(
                    color = PieColors[index % PieColors.size],
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = true
                )
                start += sweep
            }
            drawCircle(color = Color.White, radius = size.minDimension * 0.25f)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            entries.take(6).forEachIndexed { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(PieColors[index % PieColors.size], RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(6.dp))
                    Text(entry.first, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(money(entry.second), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable private fun ExpenseRow(e: Expense, money: (Double) -> String, canManage: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.item, fontWeight = FontWeight.SemiBold)
                    Text("${e.memberName} · ${e.category} · ${dateLabel(e.date)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
                Text(money(e.amount), color = Purple, fontWeight = FontWeight.Bold)
            }
            if (canManage) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onDelete) { Text("Delete", color = Color.Red) }
                }
            }
        }
    }
}

@Composable private fun EmptyState(title: String, body: String) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, fontWeight = FontWeight.Bold); Text(body, color = Color.Gray, style = MaterialTheme.typography.bodySmall) } }
