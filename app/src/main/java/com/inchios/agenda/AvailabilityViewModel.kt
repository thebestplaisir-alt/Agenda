package com.inchios.agenda

import com.inchios.agenda.android.Availability
import com.android.billingclient.api.*
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlinx.datetime.*
import java.util.*

class AvailabilityViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).availabilityDao()
    private val repository = AvailabilityRepository(dao)
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val billingClient = BillingClient.newBuilder(application)
        .setListener { billingResult, purchases ->
            if ((billingResult.responseCode == BillingClient.BillingResponseCode.OK) && (purchases != null)) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val _currentUser = MutableStateFlow(FirebaseAuth.getInstance().currentUser)
    val currentUser = _currentUser.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedDate = MutableStateFlow(Clock.System.todayIn(TimeZone.currentSystemDefault()))
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()
    
    private val _showOnlyMySessions = MutableStateFlow(value = false)
    val showOnlyMySessions = _showOnlyMySessions.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _otherUserProfile = MutableStateFlow<UserProfile?>(null)
    val otherUserProfile = _otherUserProfile.asStateFlow()

    private val _groupMembers = MutableStateFlow<List<UserProfile>>(emptyList())
    val groupMembers = _groupMembers.asStateFlow()

    private val _groups = MutableStateFlow<Map<String, Group>>(emptyMap())
    val groups = _groups.asStateFlow()

    private val _userStats = MutableStateFlow(UserStats())
    val userStats = _userStats.asStateFlow()

    private val _groupRatings = MutableStateFlow<List<MemberRatingSummary>>(emptyList())
    val groupRatings = _groupRatings.asStateFlow()

    private val _pendingMatchToValidate = MutableStateFlow<Availability?>(null)
    val pendingMatchToValidate = _pendingMatchToValidate.asStateFlow()

    private val _pendingDuoActions = MutableStateFlow<List<DuoInvitation>>(emptyList())
    val pendingDuoActions = _pendingDuoActions.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading = _isUploading.asStateFlow()

    private val registrationList = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
    private val memberRegistrationList = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
    private val ratingRegistrationList = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
    private val statsRegistrationList = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

    init {
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryPurchases()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Relancer la connexion si nécessaire
                }
            }
        )

        viewModelScope.launch {
            currentUser.collect { user ->
                if (user != null) {
                    val safeUid = user.uid.replace("[^a-zA-Z0-9-_.~%]".toRegex(), "")
                    FirebaseMessaging.getInstance().subscribeToTopic("user_$safeUid")
                    
                    fetchUserProfile(user.uid)
                    listenToUserStats(user.uid)
                    listenToInvitations(user.uid)
                } else {
                    _userProfile.value = null
                    _userStats.value = UserStats()
                    _pendingDuoActions.value = emptyList()
                    _groupMembers.value = emptyList()
                    _groups.value = emptyMap()
                }
            }
        }

        viewModelScope.launch {
            var lastGroupIds = emptySet<String>()
            userProfile.collect { profile ->
                val currentGroupIds = profile?.groupIds?.toSet() ?: emptySet()
                
                val toUnsubscribe = lastGroupIds - currentGroupIds
                toUnsubscribe.forEach { groupId ->
                    FirebaseMessaging.getInstance().unsubscribeFromTopic("group_$groupId")
                }
                
                val toSubscribe = currentGroupIds - lastGroupIds
                toSubscribe.forEach { groupId ->
                    FirebaseMessaging.getInstance().subscribeToTopic("group_$groupId")
                }
                
                lastGroupIds = currentGroupIds

                if (profile != null) {
                    fetchGroupData(profile.groupIds)
                    if (profile.currentGroupId.isNotBlank()) {
                        fetchGroupMembers(profile.currentGroupId)
                    }
                } else {
                    _groupMembers.value = emptyList()
                    _groups.value = emptyMap()
                }
            }
        }
    }

    private fun fetchGroupData(groupIds: List<String>) {
        registrationList.forEach { it.remove() }
        registrationList.clear()

        if (groupIds.isEmpty()) {
            _groups.value = emptyMap()
            return
        }

        groupIds.chunked(10).forEach { chunk ->
            val listener = firestore.collection("groups")
                .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val newGroups = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(Group::class.java)?.copy(id = doc.id)
                        }
                        val currentMap = _groups.value.toMutableMap()
                        newGroups.forEach { currentMap[it.id] = it }
                        _groups.value = currentMap
                    }
                }
            registrationList.add(listener)
        }
    }

    private fun checkAndCreateDefaultGroup(groupId: String, userName: String) {
        viewModelScope.launch {
            try {
                val groupRef = firestore.collection("groups").document(groupId)
                val snapshot = groupRef.get().await()
                if (!snapshot.exists()) {
                    val groupName = "Groupe de $userName"
                    val groupData = Group(id = groupId, name = groupName, createdBy = _currentUser.value?.uid ?: "")
                    groupRef.set(groupData).await()
                }
            } catch (_: Exception) {
                Log.e("GROUP_CHECK_ERROR", "Error checking/creating group")
            }
        }
    }

    fun updateGroupName(groupId: String, newName: String) {
        val user = _currentUser.value ?: return
        val profile = _userProfile.value ?: return
        
        if (profile.inviteCode != groupId) {
            Toast.makeText(getApplication(), "Seul le propriétaire du code $groupId peut le renommer", Toast.LENGTH_LONG).show()
            return
        }

        viewModelScope.launch {
            try {
                val groupRef = firestore.collection("groups").document(groupId)
                val groupData = Group(id = groupId, name = newName, createdBy = user.uid)
                groupRef.set(groupData).await()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Groupe mis à jour !", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Log.e("GROUP_ERROR", "Error updating group name")
            }
        }
    }

    private fun fetchGroupMembers(groupId: String) {
        memberRegistrationList.forEach { it.remove() }
        memberRegistrationList.clear()
        
        val listener = firestore.collection("profiles")
            .whereArrayContains("groupIds", groupId)
            .addSnapshotListener { snapshot, _ ->
                val members = snapshot?.documents?.mapNotNull { it.toObject(UserProfile::class.java) } ?: emptyList()
                _groupMembers.value = members
                // Passer directement la liste des membres pour éviter les décalages
                fetchGroupRatings(groupId, members)
            }
        memberRegistrationList.add(listener)
    }

    private fun fetchGroupRatings(groupId: String, members: List<UserProfile>) {
        ratingRegistrationList.forEach { it.remove() }
        ratingRegistrationList.clear()
        
        // On récupère les notes DU GROUPE pour respecter les règles de sécurité
        val listener = firestore.collection("partner_ratings")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RATINGS_ERROR", "Erreur lecture notes: ${error.message}")
                    return@addSnapshotListener
                }
                
                val ratings = snapshot?.documents ?: emptyList()
                
                val summaries = members.map { member ->
                    // On filtre SEULEMENT les notes reçues des AUTRES pour le classement
                    val othersRatings = ratings.filter { 
                        it.getString("ratedId") == member.userId && it.getString("raterId") != member.userId 
                    }.mapNotNull { it.getLong("rating")?.toInt() }
                    
                    MemberRatingSummary(
                        userId = member.userId,
                        displayName = member.displayName,
                        photoUrl = member.photoUrl,
                        averageRating = if (othersRatings.isNotEmpty()) othersRatings.average().toFloat() else 0f,
                        ratingCount = othersRatings.size
                    )
                }.sortedWith(compareByDescending<MemberRatingSummary> { it.averageRating }.thenByDescending { it.ratingCount })
                
                _groupRatings.value = summaries
            }
        ratingRegistrationList.add(listener)
    }

    private fun listenToInvitations(uid: String) {
        // Écouter les invitations reçues
        firestore.collection("duo_invitations")
            .whereEqualTo("toId", uid)
            .whereIn("status", listOf("PENDING", "PROPOSED"))
            .addSnapshotListener { _, _ ->
                updatePendingActions(uid)
            }
            
        // Écouter les invitations envoyées (pour voir les contre-propositions)
        firestore.collection("duo_invitations")
            .whereEqualTo("fromId", uid)
            .whereEqualTo("status", "PROPOSED")
            .addSnapshotListener { _, _ ->
                updatePendingActions(uid)
            }
    }

    private fun updatePendingActions(uid: String) {
        // On récupère tout ce qui concerne l'utilisateur (envoyé ou reçu)
        // en deux requêtes séparées pour respecter les règles de sécurité.
        viewModelScope.launch {
            try {
                val received = firestore.collection("duo_invitations")
                    .whereEqualTo("toId", uid)
                    .whereIn("status", listOf("PENDING", "PROPOSED"))
                    .get().await()
                
                val sentWithProposal = firestore.collection("duo_invitations")
                    .whereEqualTo("fromId", uid)
                    .whereEqualTo("status", "PROPOSED")
                    .get().await()

                val allDocs = (received.documents + sentWithProposal.documents).distinctBy { it.id }
                
                val allInvites = allDocs.mapNotNull { doc ->
                    doc.toObject(DuoInvitation::class.java)?.copy(id = doc.id)
                }

                _pendingDuoActions.value = allInvites.filter { invite ->
                    // On ne montre que si c'est à nous de jouer
                    (invite.toId == uid && invite.status == "PENDING") || 
                    (invite.status == "PROPOSED" && invite.lastProposerId != uid)
                }
            } catch (_: Exception) {
                Log.e("DUO_SYNC", "Erreur synchro invitations")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allAvailabilities: StateFlow<List<Availability>> = combine(_currentUser, _userProfile) { user, profile ->
        user to profile
    }.flatMapLatest { (user, profile) ->
        if (user != null && profile != null) {
            repository.getAvailabilities(user.uid, profile.groupIds).map { list ->
                val currentGroup = profile.currentGroupId
                list.filter { it.groupId == currentGroup || it.userId == user.uid }
                    .map { avail ->
                        // Correction pour les anciennes versions : s'assurer que participantIds contient au moins le créateur
                        if (avail.participantIds.isEmpty()) {
                            avail.copy(participantIds = listOf(avail.userId))
                        } else {
                            avail
                        }
                    }
            }
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredAvailabilities: StateFlow<List<Availability>> = combine(
        allAvailabilities,
        _searchQuery,
        _selectedDate,
        _showOnlyMySessions,
        _currentUser
    ) { list, query, date, onlyMine, user ->
        list.filter { 
            val matchesQuery = if (query.isBlank()) true else {
                it.personName.contains(query, ignoreCase = true)
            }
            val matchesDate = if (onlyMine || query.isNotBlank()) true else it.dateString == date.toString()
            val matchesUser = if (onlyMine) it.userId == user?.uid else true
            
            matchesQuery && matchesDate && matchesUser
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun listenToUserStats(uid: String) {
        statsRegistrationList.forEach { it.remove() }
        statsRegistrationList.clear()

        // Écouteur global pour l'historique et les notes qui déclenche fetchUserStats
        val hListener = firestore.collection("stats_history")
            .whereEqualTo("userId", uid)
            .addSnapshotListener { _, _ -> fetchUserStats(uid) }
        statsRegistrationList.add(hListener)

        val rListener = firestore.collection("partner_ratings")
            .whereEqualTo("ratedId", uid)
            .addSnapshotListener { _, _ -> fetchUserStats(uid) }
        statsRegistrationList.add(rListener)
        
        fetchUserStats(uid)
    }

    fun fetchUserStats(uid: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch direct pour garantir les données les plus fraîches
                val ratingsDeferred = async { fetchPartnerRatings(uid) }
                val historySnapshot = firestore.collection("stats_history")
                    .whereEqualTo("userId", uid)
                    .get().await()
                
                val distinctMatches = historySnapshot.documents
                    .filter { it.getString("type") == "match_played" }
                    .distinctBy { it.getString("availabilityId") }
                
                val total = distinctMatches.size
                if (total == 0) {
                    val (given, received) = ratingsDeferred.await()
                    val avgReceivedRating = if (received.isNotEmpty()) received.map { it.rating }.average().toFloat() else 0f
                    _userStats.value = UserStats(partnersGiven = given, partnersReceived = received, averageReceivedRating = avgReceivedRating)
                    return@launch
                }

                val duos = distinctMatches.count { doc ->
                    val isDuoFlag = doc.get("isDuo")
                    (isDuoFlag == true) || (doc.getString("partnerId") != null) || (isDuoFlag is Boolean && isDuoFlag) || (isDuoFlag is Long && isDuoFlag == 1L)
                }
                
                val sortedHistory = distinctMatches.sortedByDescending { it.getTimestamp("timestamp")?.toDate()?.time ?: 0L }
                val now = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val startOfMonth = LocalDate(now.year, now.monthNumber, 1)
                
                // Calcul du début du mois dernier
                val lastMonthDate = if (now.monthNumber == 1) {
                    LocalDate(now.year - 1, 12, 1)
                } else {
                    LocalDate(now.year, now.monthNumber - 1, 1)
                }
                
                val activityMap = mutableMapOf<String, Int>()
                var thisMonthCount = 0
                var lastMonthCount = 0
                var morningMatches = 0
                var afternoonMatches = 0
                var eveningMatches = 0
                var totalDuration = 0
                val dayCounts = mutableMapOf<String, Int>()
                val locationCounts = mutableMapOf<String, Int>()

                sortedHistory.forEach { doc ->
                    val dateStr = doc.getString("date") ?: return@forEach
                    val date = try { LocalDate.parse(dateStr) } catch(e: Exception) { return@forEach }
                    
                    activityMap[dateStr] = (activityMap[dateStr] ?: 0) + 1
                    
                    // Comparaison de dates simplifiée
                    if (date >= startOfMonth) thisMonthCount++
                    else if (date >= lastMonthDate) lastMonthCount++

                    totalDuration += doc.getLong("durationMinutes")?.toInt() ?: 60
                    
                    val hour = try { LocalTime.parse(doc.getString("time") ?: "18:00").hour } catch (e: Exception) { 18 }
                    when (hour) {
                        in 0..11 -> morningMatches++
                        in 12..17 -> afternoonMatches++
                        else -> eveningMatches++
                    }

                    // Pour le nom du jour, on garde une logique simple ou on utilise une extension
                    val dayName = date.dayOfWeek.name
                    dayCounts[dayName] = (dayCounts[dayName] ?: 0) + 1
                    doc.getString("location")?.let { if(it.isNotBlank()) locationCounts[it] = (locationCounts[it] ?: 0) + 1 }
                }

                val (given, received) = ratingsDeferred.await()
                val avgReceivedRating = if (received.isNotEmpty()) received.map { it.rating }.average().toFloat() else 0f

                _userStats.value = UserStats(
                    totalMatches = total,
                    duoMatches = duos,
                    quatuorMatches = total - duos,
                    estimatedHours = totalDuration / 60,
                    matchesThisMonth = thisMonthCount,
                    growthPercentage = if (lastMonthCount > 0) ((thisMonthCount - lastMonthCount).toFloat() / lastMonthCount * 100).toInt() else 0,
                    peakDay = dayCounts.maxByOrNull { it.value }?.key ?: "-",
                    favoriteLocation = locationCounts.maxByOrNull { it.value }?.key ?: "-",
                    caloriesBurned = totalDuration * 7,
                    morningMatches = morningMatches,
                    afternoonMatches = afternoonMatches,
                    eveningMatches = eveningMatches,
                    averageReceivedRating = avgReceivedRating,
                    partnersGiven = given,
                    partnersReceived = received,
                    activityData = activityMap
                )
            } catch (e: Exception) {
                Log.e("STATS_ERROR", "Fetch error: ${e.message}")
            }
        }
    }
    
    private suspend fun fetchPartnerRatings(uid: String): Pair<List<PartnerRating>, List<PartnerRating>> {
        val given = mutableListOf<PartnerRating>()
        val received = mutableListOf<PartnerRating>()
        
        try {
            // Notes données (ce que j'ai noté mes partenaires)
            val givenSnapshot = firestore.collection("partner_ratings")
                .whereEqualTo("raterId", uid)
                .get().await()
            givenSnapshot.documents.forEach { doc ->
                val ratedId = doc.getString("ratedId") ?: ""
                if (ratedId != uid) { // On ne s'affiche pas soi-même dans les partenaires notés
                    val rating = PartnerRating(
                        partnerId = ratedId,
                        partnerName = doc.getString("ratedName") ?: "Joueur",
                        rating = doc.getLong("rating")?.toInt() ?: 0,
                        mood = doc.getString("mood") ?: "",
                        date = doc.getString("date") ?: "",
                        groupId = doc.getString("groupId") ?: ""
                    )
                    given.add(rating)
                }
            }
            
            // Notes reçues (ce que mes partenaires m'ont noté)
            val receivedSnapshot = firestore.collection("partner_ratings")
                .whereEqualTo("ratedId", uid)
                .get().await()
            receivedSnapshot.documents.forEach { doc ->
                val raterId = doc.getString("raterId") ?: ""
                if (raterId != uid) { // On ignore l'auto-évaluation dans les notes reçues des autres
                    val rating = PartnerRating(
                        partnerId = raterId,
                        partnerName = doc.getString("raterName") ?: "Joueur",
                        rating = doc.getLong("rating")?.toInt() ?: 0,
                        mood = doc.getString("mood") ?: "",
                        date = doc.getString("date") ?: "",
                        groupId = doc.getString("groupId") ?: ""
                    )
                    received.add(rating)
                }
            }
        } catch (_: Exception) {
            Log.e("PARTNER_RATINGS", "Error fetching partner ratings")
        }
        return Pair(given, received)
    }

    fun fetchUserProfile(uid: String) {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("profiles").document(uid).get().await()

                if (snapshot.exists()) {
                    val profile = snapshot.toObject(UserProfile::class.java)
                    if (profile != null) {
                        var updated = false
                        var finalProfile = profile
                        
                        if (profile.currentGroupId.isNotBlank() && !profile.groupIds.contains(profile.currentGroupId)) {
                            finalProfile = finalProfile.copy(groupIds = (profile.groupIds + profile.currentGroupId).distinct())
                            updated = true
                        }
                        
                        if (profile.inviteCode.isBlank()) {
                            finalProfile = finalProfile.copy(inviteCode = uid.take(6).uppercase())
                            updated = true
                        }
                        
                        // S'assurer que le groupe personnel existe dans la collection 'groups'
                        checkAndCreateDefaultGroup(finalProfile.inviteCode, finalProfile.displayName)

                        if (updated) {
                            saveUserProfile(finalProfile)
                        } else {
                            _userProfile.value = finalProfile
                        }
                        
                        // Vérifier s'il y a un match à valider (pop-up)
                        checkForMatchesToValidate(uid)
                    }
                } else {
                    val inviteCode = uid.take(6).uppercase()
                    val displayName = _currentUser.value?.displayName ?: "Joueur"
                    val photoUrl = _currentUser.value?.photoUrl?.toString()
                    val newProfile = UserProfile(
                        userId = uid,
                        displayName = displayName,
                        groupIds = listOf(inviteCode),
                        currentGroupId = inviteCode,
                        inviteCode = inviteCode,
                        premium = false,
                        photoUrl = photoUrl
                    )
                    
                    // Création immédiate du groupe par défaut pour éviter le nom en code brut
                    checkAndCreateDefaultGroup(inviteCode, displayName)
                    saveUserProfile(newProfile)
                }
            } catch (_: Exception) {
                Log.e("PROFILE_ERROR", "Error fetching profile")
            }
        }
    }

    private fun checkForMatchesToValidate(uid: String) {
        viewModelScope.launch {
            try {
                val now = Clock.System.todayIn(TimeZone.currentSystemDefault())
                val todayStr = now.toString()
                
                // On cherche les matchs du jour ou d'hier où l'utilisateur est présent
                val yesterday = now.minus(1, DateTimeUnit.DAY)
                val yesterdayStr = yesterday.toString()
                
                val snapshot = firestore.collection("creneaux")
                    .whereIn("dateString", listOf(todayStr, yesterdayStr))
                    .whereArrayContains("participantIds", uid)
                    .get().await()
                
                val availabilities = snapshot.toObjects(Availability::class.java)
                val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
                
                val matchToValidate = availabilities.find { item ->
                    val isFull = if (item.isDuo) item.participantIds.size >= 2 else item.participantIds.size >= 4
                    val notValidated = !item.completedBy.contains(uid)
                    
                    val matchTime = try { LocalTime.parse(item.endTimeString) } catch (_: Exception) { LocalTime(23, 59) }
                    val isMatchFinished = if (item.dateString == todayStr) currentTime > matchTime else true
                    
                    isFull && notValidated && isMatchFinished
                }
                
                _pendingMatchToValidate.value = matchToValidate
            } catch (_: Exception) {
                Log.e("AUTO_VALIDATE", "Error checking for matches to validate")
            }
        }
    }

    fun dismissMatchToValidate() {
        _pendingMatchToValidate.value = null
    }

    fun uploadProfilePicture(uri: Uri) {
        val user = _currentUser.value ?: return
        val profile = _userProfile.value ?: return
        
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val compressedBytes = withContext(Dispatchers.IO) {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    val size = 600
                    val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
                    val width = if (ratio > 1) size else (size * ratio).toInt()
                    val height = if (ratio > 1) (size / ratio).toInt() else size
                    val scaledBitmap = originalBitmap.scale(width, height, true)
                    val outputStream = ByteArrayOutputStream()
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
                    outputStream.toByteArray()
                }

                val ref = storage.reference.child("profile_pics/${user.uid}.jpg")
                ref.putBytes(compressedBytes).await()
                val downloadUrl = ref.downloadUrl.await().toString()
                
                val updatedProfile = profile.copy(photoUrl = downloadUrl)
                saveUserProfile(updatedProfile)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Photo mise à jour !", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Log.e("UPLOAD_ERROR", "Error uploading photo")
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun joinGroup(code: String) {
        val currentProfile = _userProfile.value ?: return
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) return

        if (currentProfile.groupIds.contains(cleanCode)) {
            switchGroup(cleanCode)
            return
        }

        if (!currentProfile.premium && currentProfile.groupIds.isNotEmpty()) {
            Toast.makeText(getApplication(), "Limite de groupes atteinte. Devenez Premium !", Toast.LENGTH_LONG).show()
            return
        }

        val updatedProfile = currentProfile.copy(
            groupIds = (currentProfile.groupIds + cleanCode).distinct(),
            currentGroupId = cleanCode
        )
        saveUserProfile(updatedProfile)
    }

    fun leaveGroup(groupId: String) {
        val currentProfile = _userProfile.value ?: return
        if (currentProfile.groupIds.size <= 1) return

        val newGroupIds = currentProfile.groupIds.filter { it != groupId }
        val newCurrentGroup = if (currentProfile.currentGroupId == groupId) newGroupIds.first() else currentProfile.currentGroupId
        
        saveUserProfile(currentProfile.copy(groupIds = newGroupIds, currentGroupId = newCurrentGroup))
    }

    fun switchGroup(groupId: String) {
        val currentProfile = _userProfile.value ?: return
        if (currentProfile.currentGroupId == groupId) return
        
        saveUserProfile(currentProfile.copy(currentGroupId = groupId))
    }

    fun fetchOtherUserProfile(uid: String) {
        _otherUserProfile.value = null
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("profiles").document(uid).get().await()
                if (snapshot.exists()) {
                    _otherUserProfile.value = snapshot.toObject(UserProfile::class.java)
                } else {
                    _otherUserProfile.value = UserProfile(userId = uid, displayName = "Joueur inconnu")
                }
            } catch (_: Exception) {
                Log.e("OTHER_PROFILE_ERROR", "Error fetching other profile")
            }
        }
    }

    fun clearOtherUserProfile() {
        _otherUserProfile.value = null
    }

    fun saveUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            try {
                firestore.collection("profiles").document(profile.userId).set(profile).await()
                _userProfile.value = profile
            } catch (_: Exception) {
                Log.e("SAVE_ERROR", "Error saving profile")
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSelectedDate(date: kotlinx.datetime.LocalDate) {
        _selectedDate.value = date
    }

    fun updateCurrentUser(user: FirebaseUser?) {
        _currentUser.value = user
    }
    
    fun toggleMySessions() {
        _showOnlyMySessions.value = !_showOnlyMySessions.value
    }

    private fun getTopicName(dateString: String, groupId: String): String {
        val finalGroup = groupId.ifBlank { "default" }
        return "match_${finalGroup}_$dateString".replace("-", "_").replace(":", "_")
    }

    fun addAvailability(name: String, date: kotlinx.datetime.LocalDate, startTime: kotlinx.datetime.LocalTime, endTime: kotlinx.datetime.LocalTime, location: String = "", comment: String = "", isDuo: Boolean = false, secondaryName: String? = null) {
        val user = _currentUser.value
        val profile = _userProfile.value
        if (user == null || profile == null) return

        val profileName = profile.displayName.ifBlank { (user.displayName ?: "Joueur") }
        
        // Si secondaryName est présent (cas Moi + Ami), on combine les noms et on force le mode Duo
        val finalName = if (!secondaryName.isNullOrBlank()) {
            "$profileName & $secondaryName"
        } else {
            name.ifBlank { profileName }
        }
        
        val finalIsDuo = isDuo || !secondaryName.isNullOrBlank()
        val finalPhoto = profile.photoUrl ?: user.photoUrl?.toString()
        
        // Si c'est un Duo "Moi + Ami", on ajoute un ID fictif pour que le match soit considéré comme complet (2/2)
        val participants = if (!secondaryName.isNullOrBlank()) {
            listOf(user.uid, "GUEST_${UUID.randomUUID().toString().take(8)}")
        } else {
            listOf(user.uid)
        }

        val newAvailability = Availability(
            id = UUID.randomUUID().toString(),
            personName = finalName,
            dateString = date.toString(),
            startTimeString = startTime.toString(),
            endTimeString = endTime.toString(),
            userId = user.uid,
            userPhoto = if (name.isBlank() || name == profileName) finalPhoto else null,
            location = location,
            comment = comment,
            groupId = profile.currentGroupId,
            isDuo = finalIsDuo,
            isUserPremium = profile.premium,
            isCompleted = false,
            participantIds = participants,
            completedBy = emptyList()
        )

        viewModelScope.launch {
            try {
                repository.addAvailability(newAvailability)
                FirebaseMessaging.getInstance().subscribeToTopic(getTopicName(newAvailability.dateString, profile.currentGroupId))
            } catch (_: Exception) {
                Log.e("ADD_ERROR", "Error adding session")
            }
        }
    }

    fun completeMatch(availability: Availability, ratings: Map<String?, Int>, mood: String, notes: String, durationMinutes: Int = 60) {
        val user = _currentUser.value ?: return
        val profile = _userProfile.value ?: return
        
        // On ne bloque plus ici, on gère les permissions au moment de l'écriture

        viewModelScope.launch {
            try {
                val isActuallyDuo = availability.isDuo || availability.personName.contains(" & ")
                val newCompletedBy = (availability.completedBy + user.uid).distinct()
                val isFullyCompleted = if (isActuallyDuo) {
                    newCompletedBy.size >= 2 || availability.personName.contains(" & ")
                } else {
                    true
                }
                
                val mySelfRating = ratings[null] ?: 0
                val updatedPartnerRatings = availability.partnerRatings.toMutableMap()
                ratings.forEach { (pid, rate) -> if (pid != null) updatedPartnerRatings[pid] = rate }
                
                val updated = availability.copy(
                    isCompleted = isFullyCompleted,
                    isDuo = isActuallyDuo,
                    completedBy = newCompletedBy,
                    rating = if (mySelfRating > 0) mySelfRating else availability.rating,
                    mood = mood,
                    reviewNotes = notes,
                    durationMinutes = durationMinutes,
                    partnerRatings = updatedPartnerRatings
                )

                // 1. Essayer de mettre à jour le créneau (peut échouer si on n'est pas proprio)
                try {
                    repository.updateAvailability(updated)
                } catch (e: Exception) {
                    Log.d("COMPLETE_MATCH", "Mise à jour créneau ignorée (pas les droits), mais on continue...")
                }
                
                val currentGroup = availability.groupId.ifBlank { profile.currentGroupId }

                // 2. Enregistrer TOUTES les notes (Indépendant du créneau)
                ratings.forEach { (targetId, rate) ->
                    if (rate > 0) {
                        val finalTargetId = targetId ?: user.uid
                        val finalTargetName = if (targetId == null) (profile.displayName.ifBlank { user.displayName ?: "Moi" }) 
                                              else _groupMembers.value.find { it.userId == targetId }?.displayName ?: "Partenaire"
                        
                        val ratingId = "${user.uid}_${finalTargetId}_${availability.id}"
                        val ratingDoc = mapOf(
                            "raterId" to user.uid,
                            "raterName" to (profile.displayName.ifBlank { user.displayName ?: "Moi" }),
                            "ratedId" to finalTargetId,
                            "ratedName" to finalTargetName,
                            "availabilityId" to availability.id,
                            "rating" to rate,
                            "mood" to mood,
                            "notes" to notes,
                            "date" to availability.dateString,
                            "groupId" to currentGroup,
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        firestore.collection("partner_ratings").document(ratingId).set(ratingDoc).await()
                    }
                }
                
                // 3. Historique des stats personnel (Indépendant du créneau)
                val statsId = "${user.uid}_${availability.id}"
                firestore.collection("stats_history").document(statsId).set(mapOf(
                    "userId" to user.uid,
                    "availabilityId" to availability.id,
                    "type" to "match_played",
                    "date" to availability.dateString,
                    "time" to availability.startTimeString,
                    "location" to availability.location,
                    "rating" to mySelfRating,
                    "mood" to mood,
                    "notes" to notes,
                    "durationMinutes" to durationMinutes,
                    "isDuo" to isActuallyDuo,
                    "groupId" to currentGroup,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )).await()
                
                fetchUserStats(user.uid)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Match et notes validés !", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("COMPLETE_MATCH", "Erreur critique validation: ${e.message}")
            }
        }
    }

    fun deleteAvailability(availability: Availability) {
        val user = _currentUser.value
        val profile = _userProfile.value
        if (user?.uid == availability.userId && profile != null) {
            viewModelScope.launch {
                try {
                    repository.deleteAvailability(availability)
                    val topic = getTopicName(availability.dateString, profile.currentGroupId)
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                } catch (_: Exception) {
                    Log.e("DELETE_ERROR", "Error deleting session")
                }
            }
        }
    }

    fun proposeDuo(targetAvailability: Availability) {
        val user = _currentUser.value ?: return
        val profile = _userProfile.value
        if (user.uid == targetAvailability.userId) return

        val fromName = if (profile != null && profile.displayName.isNotBlank()) profile.displayName else (user.displayName ?: "Un joueur")

        viewModelScope.launch {
            try {
                val invitation = DuoInvitation(
                    fromId = user.uid,
                    fromName = fromName,
                    toId = targetAvailability.userId,
                    toName = targetAvailability.personName,
                    availabilityId = targetAvailability.id,
                    status = "PENDING",
                    date = targetAvailability.dateString,
                    time = "${targetAvailability.startTimeString} - ${targetAvailability.endTimeString}",
                    proposedStartTime = targetAvailability.startTimeString,
                    proposedEndTime = targetAvailability.endTimeString,
                    lastProposerId = user.uid
                )
                firestore.collection("duo_invitations").add(invitation)
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Demande Duo envoyée !", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Log.e("DUO_ERROR", "Error sending duo invitation")
            }
        }
    }

    fun updateDuoProposal(invitationId: String, startTime: kotlinx.datetime.LocalTime, endTime: kotlinx.datetime.LocalTime) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                firestore.collection("duo_invitations").document(invitationId).update(
                    "status", "PROPOSED",
                    "proposedStartTime", startTime.toString(),
                    "proposedEndTime", endTime.toString(),
                    "lastProposerId", user.uid
                ).await()

                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Horaire proposé !", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Log.e("DUO_ERROR", "Error proposing time")
            }
        }
    }

    fun confirmDuo(invitation: DuoInvitation) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                firestore.collection("duo_invitations").document(invitation.id).update(
                    "status", "CONFIRMED",
                    "lastProposerId", user.uid
                ).await()
                
                val availability = repository.getAvailabilityById(invitation.availabilityId)
                if (availability != null) {
                    val newParticipants = (availability.participantIds + invitation.fromId + invitation.toId).distinct()
                    val newPersonName = "${invitation.fromName} & ${invitation.toName}"
                    
                    val updatedAvailability = availability.copy(
                        personName = newPersonName,
                        isDuo = true,
                        startTimeString = invitation.proposedStartTime,
                        endTimeString = invitation.proposedEndTime,
                        comment = (if(availability.comment.isNotBlank()) "${availability.comment}\n" else "") + "Duo confirmé",
                        participantIds = newParticipants
                    )
                    repository.updateAvailability(updatedAvailability)

                    // Enregistrer stats Duo
                    firestore.collection("duo_stats").add(mapOf(
                        "player1Id" to invitation.fromId,
                        "player2Id" to user.uid,
                        "availabilityId" to invitation.availabilityId,
                        "date" to invitation.date,
                        "time" to invitation.proposedStartTime,
                        "location" to availability.location,
                        "groupId" to availability.groupId,
                        "status" to "CONFIRMED",
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )).await()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Match validé et fixé !", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Log.e("DUO_ERROR", "Error confirming duo")
            }
        }
    }

    fun declineDuo(invitationId: String) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                firestore.collection("duo_invitations").document(invitationId).update(
                    "status", "DECLINED",
                    "lastProposerId", user.uid
                ).await()
            } catch (_: Exception) {
                Log.e("DUO_ERROR", "Error declining duo")
            }
        }
    }

    fun contactViaWhatsApp(phone: String) {
        val cleanPhone = phone.replace("[^0-9]".toRegex(), "")
        val url = "https://api.whatsapp.com/send?phone=$cleanPhone"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = url.toUri()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            getApplication<Application>().startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(getApplication(), "WhatsApp n'est pas installé", Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        val user = _currentUser.value ?: return
        viewModelScope.launch {
            try {
                val availSnapshot = firestore.collection("creneaux").whereEqualTo("userId", user.uid).get().await()
                val batch = firestore.batch()
                availSnapshot.documents.forEach { batch.delete(it.reference) }
                
                val statsSnapshot = firestore.collection("stats_history").whereEqualTo("userId", user.uid).get().await()
                statsSnapshot.documents.forEach { batch.delete(it.reference) }
                
                batch.delete(firestore.collection("profiles").document(user.uid))
                
                batch.commit().await()

                try { storage.reference.child("profile_pics/${user.uid}.jpg").delete().await() } catch (_: Exception) {}

                user.delete().await()
                
                _currentUser.value = null
                _userProfile.value = null
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Compte supprimé définitivement.", Toast.LENGTH_LONG).show()
                    onComplete()
                }
            } catch (_: Exception) {
                Log.e("DELETE_ACCOUNT", "Error")
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Erreur : Veuillez vous reconnecter puis réessayer.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun becomePremium(activity: Activity) {
        val skuList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("premium_upgrade")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(skuList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]
                val billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                .setProductDetails(productDetails)
                                .build()
                        )
                    )
                    .build()
                billingClient.launchBillingFlow(activity, billingFlowParams)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val profile = _userProfile.value ?: return@acknowledgePurchase
                        saveUserProfile(profile.copy(premium = true))
                        viewModelScope.launch(Dispatchers.Main) {
                            Toast.makeText(getApplication(), "Félicitations ! Vous êtes Premium !", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                // Déjà acquitté, mais on s'assure que le profil est premium localement
                val profile = _userProfile.value
                if (profile != null && !profile.premium) {
                    saveUserProfile(profile.copy(premium = true))
                }
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        }
    }
}
