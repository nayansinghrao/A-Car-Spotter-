package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ocr.PlateOcrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SPOTR", appName)
    }

    @Test
    fun `test standard Indian registration plate extraction and normalization`() {
        val rawOcr = "IND \n MH 12 AB 1234 \n INDIA"
        val result = PlateOcrEngine.extractPlateText(rawOcr)

        assertEquals("MH 12 AB 1234", result.formattedPlate)
        assertEquals("MH12AB1234", result.normalizedPlate)
    }

    @Test
    fun `test Bharat Series BH plate extraction and normalization`() {
        val rawOcr = "22 BH 9999 AA"
        val result = PlateOcrEngine.extractPlateText(rawOcr)

        assertEquals("22 BH 9999 AA", result.formattedPlate)
        assertEquals("22BH9999AA", result.normalizedPlate)
    }

    @Test
    fun `test plate normalization removes special characters and spaces`() {
        val normalized = PlateOcrEngine.normalizePlate("dl-01-c-4321")
        assertEquals("DL01C4321", normalized)
    }

    @Test
    fun `test CameraCaptureHelper creates valid output file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val photoFile = com.example.util.CameraCaptureHelper.createOutputPhotoFile(context)
        assertTrue(photoFile.name.startsWith("CAR_"))
        assertTrue(photoFile.name.endsWith(".jpg"))
        assertTrue(photoFile.parentFile?.exists() == true)
    }

    @Test
    fun `test empty sighting draft derives all missing fields`() {
        val emptyDraft = com.example.data.model.Sighting(
            id = "draft-123",
            garageId = "garage-abc",
            status = "draft"
        )
        assertTrue(emptyDraft.isDraft)
        org.junit.Assert.assertFalse(emptyDraft.isComplete)

        val missing = emptyDraft.missingFields
        assertTrue(missing.contains("Photo missing"))
        assertTrue(missing.contains("Brand needed"))
        assertTrue(missing.contains("Model needed"))
        assertTrue(missing.contains("Plate unconfirmed"))
        assertTrue(missing.contains("Location missing"))
    }

    @Test
    fun `test partial sighting draft only identifies actual missing fields`() {
        val partialDraft = com.example.data.model.Sighting(
            id = "draft-456",
            garageId = "garage-abc",
            status = "draft",
            brand = "Porsche",
            model = "911 GT3 RS",
            numberPlateRaw = "MH 12 AB 1234",
            numberPlateNormalized = "MH12AB1234"
        )
        assertTrue(partialDraft.isDraft)
        val missing = partialDraft.missingFields
        assertTrue(missing.contains("Photo missing"))
        assertTrue(missing.contains("Location missing"))
        org.junit.Assert.assertFalse(missing.contains("Brand needed"))
        org.junit.Assert.assertFalse(missing.contains("Model needed"))
        org.junit.Assert.assertFalse(missing.contains("Plate unconfirmed"))
    }

    @Test
    fun `test completed sighting with all fields has empty missing fields`() {
        val completeSighting = com.example.data.model.Sighting(
            id = "sighting-789",
            garageId = "garage-abc",
            status = "complete",
            brand = "Ferrari",
            model = "SF90 Stradale",
            numberPlateRaw = "DL 01 C 4321",
            numberPlateNormalized = "DL01C4321",
            primaryImagePath = "data:image/jpeg;base64,...",
            latitude = 19.0760,
            longitude = 72.8777
        )
        assertTrue(completeSighting.isComplete)
        org.junit.Assert.assertFalse(completeSighting.isDraft)
        assertTrue(completeSighting.missingFields.isEmpty())
    }

    @Test
    fun `test sighting sync status flags`() {
        val syncedSighting = com.example.data.model.Sighting(
            id = "s-1",
            garageId = "g-1",
            syncStatus = "synced"
        )
        assertTrue(syncedSighting.isSynced)
        org.junit.Assert.assertFalse(syncedSighting.isSyncing)
        org.junit.Assert.assertFalse(syncedSighting.isSyncFailed)

        val syncingSighting = com.example.data.model.Sighting(
            id = "s-2",
            garageId = "g-1",
            syncStatus = "syncing"
        )
        assertTrue(syncingSighting.isSyncing)
        org.junit.Assert.assertFalse(syncingSighting.isSynced)
        org.junit.Assert.assertFalse(syncingSighting.isSyncFailed)

        val failedSighting = com.example.data.model.Sighting(
            id = "s-3",
            garageId = "g-1",
            syncStatus = "failed"
        )
        assertTrue(failedSighting.isSyncFailed)
        org.junit.Assert.assertFalse(failedSighting.isSynced)
        org.junit.Assert.assertFalse(failedSighting.isSyncing)
    }

    @Test
    fun `test editing sighting preserves stable document ID`() {
        val initialId = "stable-doc-id-12345"
        val original = com.example.data.model.Sighting(
            id = initialId,
            garageId = "garage-main",
            brand = "Lamborghini",
            model = "Huracan Tecnica",
            status = "draft"
        )

        // Simulate user editing model and completing draft
        val edited = original.copy(
            model = "Huracan STO",
            status = "complete",
            numberPlateRaw = "KA 01 MJ 9000",
            numberPlateNormalized = "KA01MJ9000"
        )

        assertEquals("Document ID must be preserved across edits", initialId, edited.id)
        assertEquals("Garage ID must be preserved across edits", "garage-main", edited.garageId)
        assertEquals("Huracan STO", edited.model)
        assertTrue(edited.isComplete)
    }

    @Test
    fun `test in-memory sorting of sightings puts newest or pending first`() {
        val s1 = com.example.data.model.Sighting(
            id = "s-1",
            garageId = "g-1",
            brand = "Old Car",
            createdAt = com.google.firebase.Timestamp(1000, 0),
            sightingDate = "2026-01-01",
            sightingTime = "10:00"
        )
        val s2 = com.example.data.model.Sighting(
            id = "s-2",
            garageId = "g-1",
            brand = "Newer Car",
            createdAt = com.google.firebase.Timestamp(2000, 0),
            sightingDate = "2026-02-01",
            sightingTime = "12:00"
        )
        val sPending = com.example.data.model.Sighting(
            id = "s-pending",
            garageId = "g-1",
            brand = "Just Added Car",
            createdAt = null, // pending server timestamp
            sightingDate = "2026-03-01",
            sightingTime = "14:00"
        )

        val list = listOf(s1, s2, sPending)
        val sorted = list.sortedWith(
            compareByDescending<com.example.data.model.Sighting> { it.createdAt?.seconds ?: Long.MAX_VALUE }
                .thenByDescending { it.sightingDate + it.sightingTime }
        )

        // The pending one (createdAt == null -> Long.MAX_VALUE) should be first
        assertEquals("s-pending", sorted[0].id)
        assertEquals("s-2", sorted[1].id)
        assertEquals("s-1", sorted[2].id)
    }

    @Test
    fun `test garage 2-member limit check`() {
        val garageOneMember = com.example.data.model.Garage(
            id = "g-1",
            name = "Supercars Club",
            ownerUid = "user-1",
            memberUids = listOf("user-1"),
            inviteCode = "SPT-TEST"
        )
        org.junit.Assert.assertFalse(garageOneMember.isFull)
        assertEquals(1, garageOneMember.memberCount)

        val garageFull = garageOneMember.copy(
            memberUids = listOf("user-1", "user-2")
        )
        assertTrue(garageFull.isFull)
        assertEquals(2, garageFull.memberCount)
    }
}
