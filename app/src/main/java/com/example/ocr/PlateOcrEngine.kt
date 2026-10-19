package com.example.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.resume

object PlateOcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Standard Indian plate pattern: State(2) RTO(2) Series(1-3) Number(4)
    // E.g.: MH 12 AB 1234, DL 01 C 9999, KA 05 MN 4321
    private val STANDARD_PLATE_REGEX = Pattern.compile(
        "\\b([A-Z]{2})[\\s-]?([0-9]{1,2})[\\s-]?([A-Z]{0,3})[\\s-]?([0-9]{4})\\b"
    )

    // Bharat Series (BH): E.g.: 22 BH 1234 AA
    private val BH_PLATE_REGEX = Pattern.compile(
        "\\b([0-9]{2})[\\s-]?(BH)[\\s-]?([0-9]{4})[\\s-]?([A-Z]{1,2})\\b"
    )

    // General fallback alphanumeric plate pattern (4-12 characters)
    private val GENERAL_PLATE_REGEX = Pattern.compile(
        "\\b[A-Z0-9]{6,11}\\b"
    )

    data class OcrResult(
        val detectedRaw: String,
        val formattedPlate: String,
        val normalizedPlate: String,
        val confidenceNote: String
    )

    suspend fun recognizePlateFromBitmap(bitmap: Bitmap): OcrResult =
        suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val fullText = visionText.text
                    val detected = extractPlateText(fullText)
                    continuation.resume(detected)
                }
                .addOnFailureListener {
                    continuation.resume(
                        OcrResult(
                            detectedRaw = "",
                            formattedPlate = "",
                            normalizedPlate = "",
                            confidenceNote = "Recognition failed: ${it.localizedMessage ?: "Unknown error"}"
                        )
                    )
                }
        }

    fun extractPlateText(rawOcrText: String): OcrResult {
        if (rawOcrText.isBlank()) {
            return OcrResult("", "", "", "No text detected")
        }

        // Clean up common noise and symbols, keep alphanumeric and spaces
        val cleanUpper = rawOcrText.uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 1. Try Standard Indian Plate regex
        val stdMatcher = STANDARD_PLATE_REGEX.matcher(cleanUpper)
        if (stdMatcher.find()) {
            val state = stdMatcher.group(1).orEmpty()
            val rto = stdMatcher.group(2).orEmpty().padStart(2, '0')
            val series = stdMatcher.group(3).orEmpty()
            val num = stdMatcher.group(4).orEmpty()

            val formatted = if (series.isNotEmpty()) {
                "$state $rto $series $num"
            } else {
                "$state $rto $num"
            }
            val normalized = normalizePlate(formatted)
            return OcrResult(
                detectedRaw = stdMatcher.group(0).orEmpty(),
                formattedPlate = formatted,
                normalizedPlate = normalized,
                confidenceNote = "Detected standard Indian registration format"
            )
        }

        // 2. Try Bharat Series (BH)
        val bhMatcher = BH_PLATE_REGEX.matcher(cleanUpper)
        if (bhMatcher.find()) {
            val year = bhMatcher.group(1).orEmpty()
            val bh = bhMatcher.group(2).orEmpty()
            val num = bhMatcher.group(3).orEmpty()
            val series = bhMatcher.group(4).orEmpty()
            val formatted = "$year $bh $num $series"
            return OcrResult(
                detectedRaw = bhMatcher.group(0).orEmpty(),
                formattedPlate = formatted,
                normalizedPlate = normalizePlate(formatted),
                confidenceNote = "Detected Bharat Series (BH) format"
            )
        }

        // 3. Fallback: look for lines or words with 6-11 alphanumeric characters
        val genMatcher = GENERAL_PLATE_REGEX.matcher(cleanUpper.replace(" ", ""))
        if (genMatcher.find()) {
            val found = genMatcher.group(0).orEmpty()
            return OcrResult(
                detectedRaw = found,
                formattedPlate = found,
                normalizedPlate = normalizePlate(found),
                confidenceNote = "Detected alphanumeric plate sequence"
            )
        }

        // None matched cleanly: return raw first non-empty line
        val firstLine = cleanUpper.lines().firstOrNull { it.isNotBlank() } ?: cleanUpper
        return OcrResult(
            detectedRaw = firstLine.take(15),
            formattedPlate = firstLine.take(15),
            normalizedPlate = normalizePlate(firstLine.take(15)),
            confidenceNote = "Partial match detected. Please verify."
        )
    }

    fun normalizePlate(input: String): String {
        return input.uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]"), "")
            .trim()
    }
}
