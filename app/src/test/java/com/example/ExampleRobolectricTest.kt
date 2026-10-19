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
}
