package com.example.data.repository

import android.content.Context
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreProvider {
    @Volatile
    private var instance: FirebaseFirestore? = null

    fun get(context: Context): FirebaseFirestore {
        return instance ?: synchronized(this) {
            instance ?: run {
                val dbId = context.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
                instance = firestore
                firestore
            }
        }
    }
}
