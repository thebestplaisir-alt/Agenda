package com.inchios.agenda

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AvailabilityRepository(private val dao: AvailabilityDao) {
    private val firestore = FirebaseFirestore.getInstance()
    private val collection = firestore.collection("creneaux")

    fun getAvailabilities(userId: String, groupIds: List<String>): Flow<List<Availability>> = callbackFlow {
        Log.d("FIRESTORE_SYNC", "Tentative d'écoute filtrée pour l'utilisateur $userId...")
        
        var query: Query = collection

        if (groupIds.isNotEmpty()) {
            // On récupère les séances créées par l'utilisateur OU appartenant à ses groupes
            // On utilise Filter.or pour l'isolation des données par groupe
            val groupsFilter = Filter.inArray("groupId", groupIds.take(30))
            val userFilter = Filter.equalTo("userId", userId)
            
            query = collection.where(Filter.or(userFilter, groupsFilter))
        } else {
            // Si l'utilisateur n'est dans aucun groupe, il ne voit que ses propres séances
            query = collection.whereEqualTo("userId", userId)
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FIRESTORE_SYNC", "Erreur d'écoute : ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val list = snapshot.toObjects(Availability::class.java)
                Log.d("FIRESTORE_SYNC", "Données reçues ! Nombre : ${list.size}")
                trySend(list)
            }
        }

        awaitClose { 
            subscription.remove() 
        }
    }

    suspend fun getAvailabilityById(id: String): Availability? {
        return try {
            collection.document(id).get().await().toObject(Availability::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateAvailability(availability: Availability) {
        try {
            collection.document(availability.id).set(availability).await()
            dao.insertAvailability(availability)
        } catch (e: Exception) {
            Log.e("FIRESTORE_WRITE", "Erreur mise à jour : ${e.message}")
            throw e
        }
    }

    suspend fun addAvailability(availability: Availability) {
        try {
            collection.document(availability.id).set(availability).await()
            dao.insertAvailability(availability)
        } catch (e: Exception) {
            Log.e("FIRESTORE_WRITE", "Erreur écriture : ${e.message}")
            throw e
        }
    }

    suspend fun deleteAvailability(availability: Availability) {
        try {
            collection.document(availability.id).delete().await()
            dao.deleteAvailability(availability)
        } catch (e: Exception) {
            Log.e("FIRESTORE_WRITE", "Erreur suppression : ${e.message}")
            throw e
        }
    }
}
