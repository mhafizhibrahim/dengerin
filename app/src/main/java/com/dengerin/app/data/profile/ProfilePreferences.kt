package com.dengerin.app.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(name = "dengerin_profile")

/** Cache lokal profil: UI terbaca instan tanpa menunggu Firestore. */
class ProfilePreferences(context: Context) {
    private val dataStore = context.applicationContext.profileDataStore

    val profile: Flow<UserProfile?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            if (prefs[uidKey].isNullOrBlank()) null else UserProfile(
                uid = prefs[uidKey].orEmpty(),
                displayName = prefs[displayNameKey].orEmpty(),
                email = prefs[emailKey].orEmpty(),
                photoUrl = prefs[photoUrlKey].orEmpty(),
                photoBase64 = prefs[photoBase64Key].orEmpty(),
                bio = prefs[bioKey].orEmpty(),
                totalListeningSeconds = prefs[listeningKey] ?: 0L,
                totalXp = prefs[xpKey] ?: 0L,
                totalSongsPlayed = prefs[songsKey] ?: 0L,
                nightListeningMinutes = prefs[nightKey] ?: 0L,
                equippedFrameId = prefs[frameKey] ?: UserProfile.DEFAULT_FRAME_ID,
                equippedTitle1Id = prefs[title1Key] ?: UserProfile.DEFAULT_TITLE_ID,
                equippedTitle2Id = prefs[title2Key].orEmpty(),
            )
        }

    suspend fun save(profile: UserProfile) {
        dataStore.edit { prefs ->
            prefs[uidKey] = profile.uid
            prefs[displayNameKey] = profile.displayName
            prefs[emailKey] = profile.email
            prefs[photoUrlKey] = profile.photoUrl
            prefs[photoBase64Key] = profile.photoBase64
            prefs[bioKey] = profile.bio
            prefs[listeningKey] = profile.totalListeningSeconds
            prefs[xpKey] = profile.totalXp
            prefs[songsKey] = profile.totalSongsPlayed
            prefs[nightKey] = profile.nightListeningMinutes
            prefs[frameKey] = profile.equippedFrameId
            prefs[title1Key] = profile.equippedTitle1Id
            prefs[title2Key] = profile.equippedTitle2Id
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.clear() }
    }

    private companion object {
        val uidKey = stringPreferencesKey("uid")
        val displayNameKey = stringPreferencesKey("displayName")
        val emailKey = stringPreferencesKey("email")
        val photoUrlKey = stringPreferencesKey("photoUrl")
        val photoBase64Key = stringPreferencesKey("photoBase64")
        val bioKey = stringPreferencesKey("bio")
        val listeningKey = longPreferencesKey("totalListeningSeconds")
        val xpKey = longPreferencesKey("totalXp")
        val songsKey = longPreferencesKey("totalSongsPlayed")
        val nightKey = longPreferencesKey("nightListeningMinutes")
        val frameKey = stringPreferencesKey("equippedFrameId")
        val title1Key = stringPreferencesKey("equippedTitle1Id")
        val title2Key = stringPreferencesKey("equippedTitle2Id")
    }
}
