package com.dengerin.app

import com.dengerin.app.data.profile.CREATOR_TITLE_ID
import com.dengerin.app.data.profile.DEVELOPER_EMAIL
import com.dengerin.app.data.profile.UserProfile
import com.dengerin.app.data.profile.isDeveloper
import com.dengerin.app.presentation.profile.equippedTitles
import com.dengerin.app.presentation.profile.isTitleUnlocked
import com.dengerin.app.presentation.profile.titleById
import com.dengerin.app.presentation.profile.visibleProfileTitles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDeveloperAccessTest {

    @Test
    fun emailDeveloperDiterimaTanpaPeduliKapital() {
        assertTrue(isDeveloper(DEVELOPER_EMAIL))
        assertTrue(isDeveloper(DEVELOPER_EMAIL.uppercase()))
    }

    @Test
    fun emailLainDitolak() {
        assertFalse(isDeveloper("ibrahimhafiz840@gmail.com.co"))
        assertFalse(isDeveloper("developer@gmail.com"))
        assertFalse(isDeveloper(null))
        assertFalse(isDeveloper(""))
    }

    @Test
    fun creatorHanyaAdaDiKatalogDeveloper() {
        assertTrue(visibleProfileTitles(DEVELOPER_EMAIL).any { it.id == CREATOR_TITLE_ID })
        assertFalse(visibleProfileTitles("orang@gmail.com").any { it.id == CREATOR_TITLE_ID })
        assertFalse(visibleProfileTitles(null).any { it.id == CREATOR_TITLE_ID })
    }

    @Test
    fun creatorHanyaBisaDipakaiDeveloper() {
        val title = titleById(CREATOR_TITLE_ID)!!
        val profile = UserProfile(totalXp = UserProfile.xpForLevel(10))
        assertTrue(isTitleUnlocked(title, profile, 0, DEVELOPER_EMAIL))
        assertFalse(isTitleUnlocked(title, profile, 0, "orang@gmail.com"))
    }

    @Test
    fun creatorDibuangDariTitleAktifAkunNonDeveloper() {
        val profile = UserProfile(equippedTitle1Id = CREATOR_TITLE_ID)
        assertTrue(equippedTitles(profile, DEVELOPER_EMAIL).any { it.id == CREATOR_TITLE_ID })
        assertFalse(equippedTitles(profile, "orang@gmail.com").any { it.id == CREATOR_TITLE_ID })
    }

    @Test
    fun konversiLevelKeXpSesuaiFormula() {
        assertEquals(0L, UserProfile.xpForLevel(1))
        assertEquals(40L, UserProfile.xpForLevel(2))
        assertEquals(14_440L, UserProfile.xpForLevel(20))
        assertEquals(1, UserProfile.levelFromXp(UserProfile.xpForLevel(1)))
        assertEquals(20, UserProfile.levelFromXp(UserProfile.xpForLevel(20)))
    }
}
