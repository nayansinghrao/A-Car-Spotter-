package com.example.ui.sighting

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.Sighting
import com.example.data.model.VehicleCategory
import com.example.ocr.PlateOcrEngine
import com.example.ui.AutoSaveStatus
import com.example.ui.camera.CameraViewfinderScreen
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalSurfaceVariant
import com.example.ui.theme.DangerRed
import com.example.ui.theme.NearBlack
import com.example.ui.theme.SpotrGreen
import com.example.ui.theme.SpotrGreenContainer
import com.example.ui.theme.SpotrOnGreenContainer
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningAmber
import com.example.util.ImageStorageHelper
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddSightingScreen(
    garageId: String,
    initialSighting: Sighting? = null,
    autoSaveStatus: AutoSaveStatus = AutoSaveStatus.IDLE,
    onDraftChanged: (Sighting) -> Unit = {},
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onSaveSighting: suspend (Sighting, Boolean) -> Result<String>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Stable record ID preserved across drafts and edits
    val sightingId = remember {
        initialSighting?.id?.ifBlank { null } ?: UUID.randomUUID().toString()
    }
    val createdByUid = remember { initialSighting?.createdBy ?: "" }
    val creatorName = remember { initialSighting?.creatorName ?: "" }

    // Photos state: stores Pair<DisplayUriString, Base64OrLocalPath>
    val selectedImages = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            if (initialSighting != null) {
                if (initialSighting.imagePaths.isNotEmpty()) {
                    initialSighting.imagePaths.forEach { add(Pair(it, it)) }
                } else if (initialSighting.primaryImagePath.isNotBlank()) {
                    add(Pair(initialSighting.primaryImagePath, initialSighting.primaryImagePath))
                }
            }
        }
    }
    var primaryImageIndex by remember { mutableIntStateOf(0) }

    // Vehicle state
    var selectedCategory by remember {
        mutableStateOf(initialSighting?.category?.ifBlank { null } ?: VehicleCategory.SUPERCAR.label)
    }
    var brand by remember { mutableStateOf(initialSighting?.brand ?: "") }
    var model by remember { mutableStateOf(initialSighting?.model ?: "") }

    // OCR & Number plate state
    var numberPlateRaw by remember { mutableStateOf(initialSighting?.numberPlateRaw ?: "") }
    var numberPlateConfirmed by remember {
        mutableStateOf(
            initialSighting?.numberPlateRaw?.ifBlank { null }
                ?: initialSighting?.numberPlateNormalized
                ?: ""
        )
    }
    var plateOcrStatus by remember {
        mutableStateOf(initialSighting?.plateOcrStatus ?: "manual")
    }
    var isOcrRunning by remember { mutableStateOf(false) }
    var ocrFeedbackNote by remember { mutableStateOf<String?>(null) }

    // Location state
    var locationName by remember { mutableStateOf(initialSighting?.locationName ?: "") }
    var area by remember { mutableStateOf(initialSighting?.area ?: "") }
    var city by remember { mutableStateOf(initialSighting?.city ?: "") }
    var latitude by remember { mutableDoubleStateOf(initialSighting?.latitude ?: 0.0) }
    var longitude by remember { mutableDoubleStateOf(initialSighting?.longitude ?: 0.0) }
    var isCapturingLocation by remember { mutableStateOf(false) }

    // Sighting Date & Time
    val now = remember { Date() }
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.US) }
    var sightingDate by remember {
        mutableStateOf(initialSighting?.sightingDate?.ifBlank { null } ?: dateFormatter.format(now))
    }
    var sightingTime by remember {
        mutableStateOf(initialSighting?.sightingTime?.ifBlank { null } ?: timeFormatter.format(now))
    }
    var notes by remember { mutableStateOf(initialSighting?.notes ?: "") }

    // Explicit save state
    var isSavingExplicitly by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    // Helper to construct the current snapshot of Sighting
    fun buildSighting(status: String): Sighting {
        val imageStrings = selectedImages.map { it.second }
        val primary = if (primaryImageIndex in imageStrings.indices) {
            imageStrings[primaryImageIndex]
        } else imageStrings.firstOrNull() ?: ""

        return Sighting(
            id = sightingId,
            garageId = garageId,
            createdBy = createdByUid,
            creatorName = creatorName,
            status = status,
            category = selectedCategory,
            brand = brand.trim(),
            model = model.trim(),
            numberPlateRaw = numberPlateRaw.trim(),
            numberPlateNormalized = PlateOcrEngine.normalizePlate(numberPlateConfirmed),
            plateOcrStatus = plateOcrStatus,
            imagePaths = imageStrings,
            primaryImagePath = primary,
            locationName = locationName.trim(),
            area = area.trim(),
            city = city.trim(),
            latitude = latitude,
            longitude = longitude,
            sightingDate = sightingDate,
            sightingTime = sightingTime,
            notes = notes.trim(),
            createdAt = initialSighting?.createdAt
        )
    }

    // Missing fields derived dynamically
    val currentMissingFields by remember(
        selectedImages.size, primaryImageIndex, brand, model,
        numberPlateRaw, numberPlateConfirmed, latitude, longitude,
        locationName, area, city
    ) {
        derivedStateOf { buildSighting("draft").missingFields }
    }

    // Auto-save debounce trigger
    // Avoid firing on initial composition by tracking user edits
    var hasUserEdited by remember { mutableStateOf(false) }
    LaunchedEffect(
        selectedCategory, brand, model, numberPlateConfirmed,
        locationName, area, city, latitude, longitude,
        notes, sightingDate, sightingTime, selectedImages.size
    ) {
        if (!hasUserEdited) {
            hasUserEdited = true
            return@LaunchedEffect
        }
        onDraftChanged(buildSighting("draft"))
    }

    // CameraX Viewfinder state
    var showCameraViewfinder by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCameraViewfinder = true
        } else {
            Toast.makeText(context, "Camera permission required to capture vehicle photos", Toast.LENGTH_SHORT).show()
        }
    }

    if (showCameraViewfinder) {
        CameraViewfinderScreen(
            onPhotoCaptured = { photoFile ->
                showCameraViewfinder = false
                coroutineScope.launch {
                    val uri = Uri.fromFile(photoFile)
                    val processed = ImageStorageHelper.processAndSaveImage(context, uri)
                    if (processed != null) {
                        selectedImages.add(Pair(uri.toString(), processed.second))
                    }
                    // Run OCR immediately on the newly captured car photo
                    isOcrRunning = true
                    runOcrOnUri(context, uri) { result ->
                        isOcrRunning = false
                        if (result.formattedPlate.isNotBlank()) {
                            numberPlateRaw = result.detectedRaw
                            numberPlateConfirmed = result.formattedPlate
                            plateOcrStatus = "detected_confirmed"
                            ocrFeedbackNote = result.confidenceNote
                            Toast.makeText(context, "Plate recognized: ${result.formattedPlate}", Toast.LENGTH_SHORT).show()
                        } else {
                            ocrFeedbackNote = "No plate recognized. You can enter it manually."
                        }
                    }
                }
            },
            onClose = { showCameraViewfinder = false }
        )
        return
    }

    // Gallery picker launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        coroutineScope.launch {
            for (uri in uris) {
                val processed = ImageStorageHelper.processAndSaveImage(context, uri)
                if (processed != null) {
                    selectedImages.add(Pair(uri.toString(), processed.second))
                }
            }
            if (numberPlateConfirmed.isBlank() && uris.isNotEmpty()) {
                val firstUri = uris.first()
                runOcrOnUri(context, firstUri) { result ->
                    numberPlateRaw = result.detectedRaw
                    numberPlateConfirmed = result.formattedPlate
                    plateOcrStatus = "detected_confirmed"
                    ocrFeedbackNote = result.confidenceNote
                }
            }
        }
    }

    // Location Permission launcher
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isCapturingLocation = true
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    isCapturingLocation = false
                    if (loc != null) {
                        latitude = loc.latitude
                        longitude = loc.longitude
                        // Reverse geocode
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                val addr = addresses?.firstOrNull()
                                if (addr != null) {
                                    val detectedArea = addr.subLocality ?: addr.thoroughfare ?: ""
                                    val detectedCity = addr.locality ?: addr.adminArea ?: ""
                                    val detectedFeature = addr.featureName ?: ""
                                    withContext(Dispatchers.Main) {
                                        if (area.isBlank()) area = detectedArea
                                        if (city.isBlank()) city = detectedCity
                                        if (locationName.isBlank()) {
                                            locationName = listOfNotNull(detectedFeature, detectedArea, detectedCity)
                                                .filter { it.isNotBlank() }
                                                .distinct()
                                                .joinToString(", ")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Silent fallback to coordinates
                            }
                        }
                    } else {
                        Toast.makeText(context, "Location unavailable. Please enter manually.", Toast.LENGTH_SHORT).show()
                    }
                }.addOnFailureListener {
                    isCapturingLocation = false
                    Toast.makeText(context, "Failed to capture location: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: SecurityException) {
                isCapturingLocation = false
            }
        } else {
            Toast.makeText(context, "Location permission denied. Enter location manually.", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = NearBlack,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NearBlack)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                    Text(
                        text = if (initialSighting?.isDraft == true) "EDIT DRAFT" else if (initialSighting != null) "EDIT SIGHTING" else "ADD SIGHTING",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = TextWhite
                    )
                }

                // VISIBLE AUTO-SAVE STATE BADGE
                AutoSaveBadge(status = autoSaveStatus)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // MISSING FIELDS REMINDER BAR (Gentle, non-blocking guidance)
            if (currentMissingFields.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CarbonDark)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Missing Information (Draft Mode Active)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = WarningAmber
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            currentMissingFields.forEach { missingLabel ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CharcoalSurfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = missingLabel,
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 1: PHOTOS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VEHICLE PHOTOGRAPHS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = SpotrGreen
                    )
                    Text(
                        text = "${selectedImages.size} selected (Optional for draft)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Photos Horizontally Scrollable
                if (selectedImages.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        selectedImages.forEachIndexed { index, pair ->
                            val isPrimary = index == primaryImageIndex
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        2.dp,
                                        if (isPrimary) SpotrGreen else CharcoalBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { primaryImageIndex = index }
                            ) {
                                AsyncImage(
                                    model = pair.first,
                                    contentDescription = "Vehicle Photo $index",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Primary badge
                                if (isPrimary) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SpotrGreen)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("PRIMARY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = NearBlack)
                                    }
                                }

                                // Delete button
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(CarbonDark.copy(alpha = 0.85f))
                                        .clickable {
                                            selectedImages.removeAt(index)
                                            if (primaryImageIndex >= selectedImages.size) {
                                                primaryImageIndex = 0
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        tint = TextWhite,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Run OCR button on this image
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(CarbonDark.copy(alpha = 0.85f))
                                        .clickable {
                                            val uri = Uri.parse(pair.first)
                                            isOcrRunning = true
                                            coroutineScope.launch {
                                                runOcrOnUri(context, uri) { result ->
                                                    isOcrRunning = false
                                                    numberPlateRaw = result.detectedRaw
                                                    numberPlateConfirmed = result.formattedPlate
                                                    plateOcrStatus = "detected_confirmed"
                                                    ocrFeedbackNote = result.confidenceNote
                                                }
                                            }
                                        }
                                        .padding(vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("SCAN PLATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SpotrGreen)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Add Photo Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val hasCamPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasCamPermission) {
                                showCameraViewfinder = true
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CharcoalSurfaceVariant,
                            contentColor = TextWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = SpotrGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Camera")
                    }

                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CharcoalSurfaceVariant,
                            contentColor = TextWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gallery")
                    }
                }
            }

            // SECTION 2: NUMBER-PLATE OCR & CORRECTION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = SpotrGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NUMBER PLATE (ASSISTIVE OCR)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            ),
                            color = SpotrGreen
                        )
                    }

                    if (isOcrRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SpotrGreen, strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = numberPlateConfirmed,
                    onValueChange = {
                        numberPlateConfirmed = it.uppercase()
                        plateOcrStatus = if (it.isBlank()) "skipped" else "manual"
                    },
                    label = { Text("Registration Plate (Optional)") },
                    placeholder = { Text("e.g. MH 12 AB 1234 or 22 BH 0001 AA") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotrGreen,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = CarbonDark,
                        unfocusedContainerColor = CarbonDark
                    ),
                    trailingIcon = {
                        if (numberPlateConfirmed.isNotBlank()) {
                            IconButton(onClick = {
                                numberPlateConfirmed = ""
                                numberPlateRaw = ""
                                plateOcrStatus = "skipped"
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("number_plate_input")
                )

                if (ocrFeedbackNote != null) {
                    Text(
                        text = ocrFeedbackNote!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ocrFeedbackNote!!.contains("failed", ignoreCase = true)) DangerRed else SpotrGreen,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = "Normalized: ${PlateOcrEngine.normalizePlate(numberPlateConfirmed).ifBlank { "NONE" }}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextDim,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // SECTION 3: VEHICLE CLASSIFICATION
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "VEHICLE CATEGORY",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = SpotrGreen
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VehicleCategory.entries.forEach { cat ->
                        val isSelected = selectedCategory == cat.label
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat.label },
                            label = { Text(cat.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SpotrGreen,
                                selectedLabelColor = NearBlack,
                                containerColor = CarbonDark,
                                labelColor = TextWhite
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = CharcoalBorder,
                                selectedBorderColor = SpotrGreen
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Brand Selector chips
                Text(
                    text = "QUICK BRANDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickBrands = listOf("Porsche", "Ferrari", "Lamborghini", "McLaren", "BMW", "Mercedes-AMG", "Aston Martin", "Audi", "Rolls-Royce", "Bentley", "Ducati", "Kawasaki")
                    quickBrands.forEach { qBrand ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (brand.equals(qBrand, ignoreCase = true)) SpotrGreenContainer else CarbonDark)
                                .border(1.dp, if (brand.equals(qBrand, ignoreCase = true)) SpotrGreen else CharcoalBorder, RoundedCornerShape(6.dp))
                                .clickable { brand = qBrand }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = qBrand,
                                fontSize = 11.sp,
                                color = if (brand.equals(qBrand, ignoreCase = true)) SpotrOnGreenContainer else TextWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Brand input (Optional for draft)
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand / Make (Optional for draft)") },
                    placeholder = { Text("e.g. Porsche") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotrGreen,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = CarbonDark,
                        unfocusedContainerColor = CarbonDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("brand_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Model input (Optional for draft)
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model (Optional for draft)") },
                    placeholder = { Text("e.g. 911 GT3 RS") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotrGreen,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = CarbonDark,
                        unfocusedContainerColor = CarbonDark
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("model_input")
                )
            }

            // SECTION 4: LOCATION & GPS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LOCATION & GPS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = SpotrGreen
                    )

                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        enabled = !isCapturingLocation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotrGreenContainer,
                            contentColor = SpotrOnGreenContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isCapturingLocation) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SpotrOnGreenContainer, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Capture GPS", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (latitude != 0.0 || longitude != 0.0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CarbonDark)
                            .border(1.dp, SpotrGreen, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = SpotrGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GPS: ${String.format(Locale.US, "%.5f", latitude)}, ${String.format(Locale.US, "%.5f", longitude)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextWhite
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("Location Name / Landmark (Optional)") },
                    placeholder = { Text("e.g. Bandra-Worli Sea Link") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotrGreen,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = CarbonDark,
                        unfocusedContainerColor = CarbonDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = area,
                        onValueChange = { area = it },
                        label = { Text("Area / Locality") },
                        placeholder = { Text("e.g. Bandra West") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotrGreen,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = CarbonDark,
                            unfocusedContainerColor = CarbonDark
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        placeholder = { Text("e.g. Mumbai") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotrGreen,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = CarbonDark,
                            unfocusedContainerColor = CarbonDark
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // SECTION 5: DATE, TIME & NOTES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "TIMESTAMP & NOTES",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = SpotrGreen
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = sightingDate,
                        onValueChange = { sightingDate = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotrGreen,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = CarbonDark,
                            unfocusedContainerColor = CarbonDark
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = sightingTime,
                        onValueChange = { sightingTime = it },
                        label = { Text("Time (HH:mm)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotrGreen,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = CarbonDark,
                            unfocusedContainerColor = CarbonDark
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("e.g. Weissach package, Akrapovic exhaust...") },
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotrGreen,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = CarbonDark,
                        unfocusedContainerColor = CarbonDark
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // SAVE ERROR WITH RETRY
            if (saveError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CarbonDark)
                        .border(1.5.dp, DangerRed, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = DangerRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Save Failed: $saveError",
                                color = DangerRed,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            text = "Your vehicle details have not been lost. Check your connection or sign-in state, then tap Retry below.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    isSavingExplicitly = true
                                    val targetStatus = if (initialSighting?.isComplete == true) "complete" else "draft"
                                    coroutineScope.launch {
                                        val sighting = buildSighting(status = targetStatus)
                                        val result = onSaveSighting(sighting, targetStatus == "complete")
                                        isSavingExplicitly = false
                                        if (result.isSuccess) {
                                            saveError = null
                                            Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show()
                                            onSaveSuccess()
                                        } else {
                                            saveError = result.exceptionOrNull()?.localizedMessage ?: "Failed to save record"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = TextWhite),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Text("Retry Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // DUAL ACTION BUTTONS: "Save Draft" & "Save as Complete"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. SAVE DRAFT (Accepts any state, even completely blank)
                Button(
                    onClick = {
                        isSavingExplicitly = true
                        saveError = null
                        coroutineScope.launch {
                            val draftSighting = buildSighting(status = "draft")
                            val result = onSaveSighting(draftSighting, false)
                            isSavingExplicitly = false
                            if (result.isSuccess) {
                                Toast.makeText(context, "Draft saved to garage!", Toast.LENGTH_SHORT).show()
                                onSaveSuccess()
                            } else {
                                saveError = result.exceptionOrNull()?.localizedMessage ?: "Failed to save draft"
                            }
                        }
                    },
                    enabled = !isSavingExplicitly,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CharcoalCard,
                        contentColor = TextWhite
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                        .testTag("save_draft_button")
                ) {
                    if (isSavingExplicitly) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = TextWhite, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, tint = SpotrGreen, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Draft", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // 2. SAVE AS COMPLETE (Direct save, reminds gently if fields missing without blocking)
                Button(
                    onClick = {
                        isSavingExplicitly = true
                        saveError = null
                        coroutineScope.launch {
                            val completeSighting = buildSighting(status = "complete")
                            val result = onSaveSighting(completeSighting, true)
                            isSavingExplicitly = false
                            if (result.isSuccess) {
                                if (currentMissingFields.isNotEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "Saved as complete (Missing: ${currentMissingFields.joinToString(", ")})",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, "Sighting completed & logged!", Toast.LENGTH_SHORT).show()
                                }
                                onSaveSuccess()
                            } else {
                                saveError = result.exceptionOrNull()?.localizedMessage ?: "Failed to complete sighting"
                            }
                        }
                    },
                    enabled = !isSavingExplicitly,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotrGreen,
                        contentColor = NearBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(54.dp)
                        .testTag("save_complete_button")
                ) {
                    if (isSavingExplicitly) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NearBlack, strokeWidth = 2.5.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save as Complete", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoSaveBadge(status: AutoSaveStatus) {
    AnimatedVisibility(
        visible = status != AutoSaveStatus.IDLE,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CarbonDark)
                .border(
                    1.dp,
                    when (status) {
                        AutoSaveStatus.SAVING -> WarningAmber
                        AutoSaveStatus.SAVED -> SpotrGreen
                        AutoSaveStatus.FAILED -> DangerRed
                        else -> CharcoalBorder
                    },
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            when (status) {
                AutoSaveStatus.SAVING -> {
                    CircularProgressIndicator(modifier = Modifier.size(10.dp), color = WarningAmber, strokeWidth = 1.5.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Saving...", fontSize = 11.sp, color = WarningAmber, fontWeight = FontWeight.Medium)
                }
                AutoSaveStatus.SAVED -> {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SpotrGreen, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Saved", fontSize = 11.sp, color = SpotrGreen, fontWeight = FontWeight.Bold)
                }
                AutoSaveStatus.FAILED -> {
                    Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = null, tint = DangerRed, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save failed", fontSize = 11.sp, color = DangerRed, fontWeight = FontWeight.Bold)
                }
                else -> Unit
            }
        }
    }
}

private suspend fun runOcrOnUri(
    context: Context,
    uri: Uri,
    onResult: (PlateOcrEngine.OcrResult) -> Unit
) {
    val bitmap = ImageStorageHelper.loadBitmapFromUri(context, uri)
    if (bitmap != null) {
        val result = PlateOcrEngine.recognizePlateFromBitmap(bitmap)
        withContext(Dispatchers.Main) {
            onResult(result)
        }
    }
}
