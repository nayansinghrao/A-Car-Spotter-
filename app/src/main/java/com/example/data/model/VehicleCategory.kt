package com.example.data.model

enum class VehicleCategory(val label: String, val isBike: Boolean = false) {
    SUPERCAR("Supercar"),
    LUXURY_CAR("Luxury Car"),
    SPORTS_CAR("Sports Car"),
    BMW("BMW"),
    OTHER_CAR("Other Car"),
    SUPERBIKE("Superbike", true),
    MOTORCYCLE("Motorcycle", true),
    SCOOTER("Scooter", true),
    OTHER_BIKE("Other Bike", true);

    companion object {
        fun fromLabel(label: String): VehicleCategory {
            return entries.find { it.label.equals(label, ignoreCase = true) } ?: OTHER_CAR
        }
    }
}
