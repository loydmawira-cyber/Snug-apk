package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.UserProfile
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class SeedRepository(private val db: FirebaseFirestore, private val context: Context) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id)),
        context
    )

    suspend fun seedData(): Result<Unit> {
        return try {
            val packageName = context.packageName
            
            val seedUsers = listOf(
                UserProfile(
                    id = "seed_jane",
                    displayName = "Jane",
                    bio = "Kind soul, professional architect. I love city walks and deep conversations.",
                    birthDate = Timestamp(Date(92, 5, 20)),
                    gender = "Female",
                    interestedIn = listOf("Male"),
                    interests = listOf("Architecture", "Photography", "Travel"),
                    photos = listOf("android.resource://$packageName/drawable/img_jane_avatar_1791028912976"),
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                ),
                UserProfile(
                    id = "seed_peter",
                    displayName = "Peter",
                    bio = "Adventurer at heart. You'll find me on a mountain trail most weekends.",
                    birthDate = Timestamp(Date(90, 8, 15)),
                    gender = "Male",
                    interestedIn = listOf("Female"),
                    interests = listOf("Hiking", "Outdoors", "Fitness"),
                    photos = listOf("android.resource://$packageName/drawable/img_peter_avatar_1791028926931"),
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                ),
                UserProfile(
                    id = "seed_bridget",
                    displayName = "Bridget",
                    bio = "Art is my language. Looking for a muse or just someone to explore galleries with.",
                    birthDate = Timestamp(Date(95, 2, 10)),
                    gender = "Female",
                    interestedIn = listOf("Male", "Female"),
                    interests = listOf("Art", "Painting", "Music"),
                    photos = listOf("android.resource://$packageName/drawable/img_bridget_avatar_1791028942614"),
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                ),
                UserProfile(
                    id = "seed_agnes",
                    displayName = "Agnes",
                    bio = "Lifelong learner. My library is my sanctuary. Seeking a fellow bibliophile.",
                    birthDate = Timestamp(Date(88, 11, 5)),
                    gender = "Female",
                    interestedIn = listOf("Male"),
                    interests = listOf("Reading", "History", "Chess"),
                    photos = listOf("android.resource://$packageName/drawable/img_agnes_avatar_1791028955213"),
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
            )

            val batch = db.batch()
            for (user in seedUsers) {
                val docRef = db.collection("users").document(user.id)
                batch.set(docRef, user)
            }
            batch.commit().await()
            Log.d("SeedRepository", "Seed successful")
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users")
            Log.e("SeedRepository", "Seed failed", e)
            Result.failure(e)
        }
    }
}
