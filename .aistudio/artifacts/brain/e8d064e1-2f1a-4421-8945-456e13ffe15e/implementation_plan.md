# SPOTR — "Save Now, Complete Later" Draft & Auto-Save System

An end-to-end draft management and debounced auto-save system for SPOTR, enabling users to log incomplete sightings in seconds while on the move, automatically sync them across both garage members in real time, and resume or complete them later.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product decisions were confirmed during the planning phase:
> - **Auto-Save Timing**: 1.5 seconds debounce after user stops typing or modifying inputs, preventing excess database writes while ensuring work is never lost.
> - **Drafts Presentation**: Dedicated "Incomplete Drafts" alert banner on the Home dashboard and an "Incomplete" filter chip with visual badges in the Garage fleet.
> - **Save as Complete Behavior**: Direct save without a blocking confirmation dialog, allowing rapid submission while keeping missing-field indicator pills visible on draft cards.

---

## 1. Overview & Core Concept

When spotting rare vehicles on the street, spotters often only have a fraction of a second to snap a photo, or note down a registration plate, or log a GPS pin without knowing the exact model year or having time to fill out notes. 

The **Save Now, Complete Later** draft system removes all input barriers:
* **Zero Required Fields for Drafts**: Any partially completed or empty sighting can be saved as a draft.
* **Debounced Auto-Save**: Automatically commits edits to Cloud Firestore 1.5s after user interaction pauses, showing real-time `Saving...`, `Saved`, and `Save failed` states.
* **Shared 2-Person Fleet Workspace**: Both authorized garage members can view, edit, complete, or delete shared drafts, with clear attribution (`createdBy` and `lastUpdatedBy`).
* **Non-Destructive Resuming**: Resuming a draft preserves the stable Firestore record ID, prevents duplicate records, and maintains image and metadata integrity.

---

## 2. User Experience & Visual Design

### Key User Flows

```
[ Spot Vehicle ] ──> [ Open Add Sighting ]
                           │
       ┌───────────────────┴───────────────────┐
       ▼                                       ▼
 [ Quick Capture ]                       [ Type Details ]
 (CameraX Photo / Plate)                 (Brand / Notes / GPS)
       │                                       │
       └───────────────────┬───────────────────┘
                           ▼
               [ Debounced Auto-Save (1.5s) ]
               ┌────────────────────────────┐
               │ Status: Saving... -> Saved │
               └────────────────────────────┘
                           │
       ┌───────────────────┴───────────────────┐
       ▼                                       ▼
 [ "Save Draft" ]                     [ "Save as Complete" ]
 (Returns to Fleet, marked            (Marks complete, ready
  Incomplete with missing badges)      for showcase)
       │                                       │
       └───────────────────┬───────────────────┘
                           ▼
                [ Home & Garage Views ]
          • Dedicated Incomplete Banner
          • Badges: "Photo missing", "Plate unconfirmed"
          • Resume & Edit anytime
```

### Visual Styling & Status Indicators
* **Auto-Save Badge**: Top bar pill with animated state transitions:
  * *Saving*: Pulsing amber dot with `Saving draft...`
  * *Saved*: Electric green check with `Saved`
  * *Save Failed*: Crimson warning with `Offline - retrying`
* **Draft Cards**: Charcoal cards (`#191D23`) with an amber `INCOMPLETE DRAFT` tag, displaying dynamic missing-field chips:
  * `Photo missing` (when no images attached)
  * `Plate unconfirmed` (when plate is blank or skipped)
  * `Model needed` (when brand is filled but model is blank)
  * `GPS missing` (when coordinates are zero)
* **Action Bar**: Dual bottom action buttons:
  * `Save Draft` (Secondary Charcoal border button)
  * `Save as Complete` (Primary Electric Green button)

---

## 3. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                   │
│                                                        │
│  ┌──────────────────┐  ┌────────────────────────────┐  │
│  │ AddSightingScreen│  │ HomeScreen / GarageScreen  │  │
│  │ • Auto-save Flow │  │ • Incomplete Drafts Banner │  │
│  │ • Dual Actions   │  │ • Filter: All | Complete   │  │
│  │ • Missing Chips  │  │   | Incomplete             │  │
│  └─────────┬────────┘  └─────────────┬──────────────┘  │
└────────────┼─────────────────────────┼─────────────────┘
             ▼                         ▼
┌────────────────────────────────────────────────────────┐
│                     MainViewModel                      │
│  • Auto-save Job with 1500ms debounce                  │
│  • Stable Draft ID lifecycle management                │
│  • Auto-Save State Flow: Idle | Saving | Saved | Error │
└────────────────────────────┬───────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────┐
│             SightingRepository (Firestore)             │
│  • upsertSighting(garageId, sighting)                  │
│  • Status: "draft" | "complete"                        │
│  • Attribution: createdBy, creatorName, lastUpdatedBy │
│  • Server timestamps: createdAt, updatedAt             │
└────────────────────────────┬───────────────────────────┘
                             ▼
┌────────────────────────────────────────────────────────┐
│                 Cloud Firestore Engine                 │
│         /garages/{garageId}/sightings/{sightingId}     │
│         Security Rules: 2-Member ABAC Authorization   │
└────────────────────────────────────────────────────────┘
```

### Data Model Enhancements (`Sighting.kt`)

```kotlin
data class Sighting(
    val id: String = "",
    val garageId: String = "",
    val createdBy: String = "",
    val creatorName: String = "",
    val lastUpdatedBy: String = "",
    val status: String = "complete", // "draft" or "complete"
    val category: String = VehicleCategory.OTHER_CAR.label,
    val brand: String = "",
    val model: String = "",
    val numberPlateRaw: String = "",
    val numberPlateNormalized: String = "",
    val plateOcrStatus: String = "manual",
    val imagePaths: List<String> = emptyList(),
    val primaryImagePath: String = "",
    val locationName: String = "",
    val area: String = "",
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val sightingDate: String = "",
    val sightingTime: String = "",
    val notes: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    val isDraft: Boolean get() = status == "draft"

    /** Derived missing fields list calculated dynamically */
    val missingFields: List<String>
        get() = buildList {
            if (primaryImagePath.isBlank() && imagePaths.isEmpty()) add("Photo missing")
            if (brand.isBlank()) add("Brand missing")
            if (model.isBlank()) add("Model missing")
            if (numberPlateRaw.isBlank() && numberPlateNormalized.isBlank()) add("Plate unconfirmed")
            if (latitude == 0.0 && longitude == 0.0) add("Location missing")
        }
}
```

---

## 4. Key Implementation Steps

1. **Firestore Schema & Rules Update**:
   * Update `firestore.rules` and `firebase-blueprint.json` to support `status` (`'draft'` or `'complete'`) and optional attributes in `isValidSighting`.
   * Re-deploy rules to backend using `fax.DeployRules`.
2. **Sighting Model & Repository (`SightingRepository.kt`)**:
   * Add `upsertSighting` which creates the document if missing or updates fields with `lastUpdatedBy` and `updatedAt: serverTimestamp()`.
   * Support real-time synchronization of both drafts and completed sightings across both garage members.
3. **Auto-Save Engine (`MainViewModel.kt`)**:
   * Implement coroutine-based debounced auto-save (1500ms delay).
   * Stable draft ID generation upon opening form or resuming existing draft.
   * State monitoring (`AutoSaveStatus.SAVING`, `AutoSaveStatus.SAVED`, `AutoSaveStatus.FAILED`).
4. **Enhanced Add & Edit Form (`AddSightingScreen.kt`)**:
   * Top bar live auto-save pill (`Saving...` / `Saved`).
   * "Save Draft" button (saves `status = "draft"` without requiring any fields).
   * "Save as Complete" button (saves `status = "complete"`).
   * Restore all fields when resuming an existing draft.
5. **Dashboard & Fleet UI Updates (`HomeScreen.kt`, `GarageScreen.kt`)**:
   * Home: New "Incomplete Drafts" quick card showing pending count and draft thumbnails with 1-tap "Resume".
   * Garage: "Incomplete" filter chip alongside categories; draft cards feature amber outline and missing-field pills.
6. **Robolectric & Compilation Verification**:
   * Unit tests verifying draft creation, missing fields derivation, and updating without duplicates.
