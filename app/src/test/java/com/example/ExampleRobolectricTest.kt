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
}
