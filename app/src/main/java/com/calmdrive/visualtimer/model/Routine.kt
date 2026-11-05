package com.calmdrive.visualtimer.model

enum class VisualStyle(val id: String, val title: String, val icon: String) {
    DISC("disc", "Time Timer Disc", "🟢"),
    JOURNEY("journey", "Car Journey Path", "🚗"),
    BLOCKS("blocks", "Calm Blocks", "🧩");

    companion object {
        fun fromId(id: String?): VisualStyle =
            entries.find { it.id == id } ?: DISC
    }
}

enum class NumberMode(val id: String, val label: String) {
    ROUNDED("rounded", "Calm Rounded"),
    EXACT("exact", "Exact MM:SS"),
    NONE("none", "No Numbers");

    companion object {
        fun fromId(id: String?): NumberMode =
            entries.find { it.id == id } ?: ROUNDED
    }
}

enum class PaletteTheme(
    val id: String,
    val title: String,
    val highColor: Int,
    val midColor: Int,
    val lowColor: Int,
    val doneColor: Int,
    val highDot: String,
    val midDot: String,
    val lowDot: String
) {
    TRAFFIC(
        id = "traffic",
        title = "Traffic Light",
        highColor = 0xFF34D399.toInt(), // Green
        midColor = 0xFFFBBF24.toInt(),  // Amber
        lowColor = 0xFFFB7185.toInt(),  // Coral (low-anxiety red)
        doneColor = 0xFFA78BFA.toInt(), // Celebration purple
        highDot = "🟢",
        midDot = "🟡",
        lowDot = "🟠"
    ),
    OCEAN(
        id = "ocean",
        title = "Calm Ocean",
        highColor = 0xFF38BDF8.toInt(), // Sky blue
        midColor = 0xFF2DD4BF.toInt(),  // Teal
        lowColor = 0xFFC084FC.toInt(),  // Lavender
        doneColor = 0xFF34D399.toInt(), // Mint
        highDot = "🔵",
        midDot = "🟢",
        lowDot = "🟣"
    ),
    SUNSHINE(
        id = "sunshine",
        title = "High-Contrast",
        highColor = 0xFFFACC15.toInt(), // Sunshine yellow
        midColor = 0xFF22D3EE.toInt(),  // Cyan
        lowColor = 0xFFF472B6.toInt(),  // Pink
        doneColor = 0xFF4ADE80.toInt(), // Green
        highDot = "🟡",
        midDot = "🔵",
        lowDot = "🟣"
    );

    companion object {
        fun fromId(id: String?): PaletteTheme =
            entries.find { it.id == id } ?: TRAFFIC
    }
}

data class Routine(
    val id: String,
    val firstTitle: String,
    val firstIcon: String,
    val firstMins: Int,
    val thenTitle: String? = null,
    val thenIcon: String? = null,
    val thenMins: Int = 0,
    val destStreet: String = firstTitle
)

val DEFAULT_ROUTINES = listOf(
    Routine(
        id = "school-dropoff",
        firstTitle = "School Drop-off",
        firstIcon = "🏫",
        firstMins = 15,
        thenTitle = "Drop-off Hug & Backpack",
        thenIcon = "🎒",
        thenMins = 2,
        destStreet = "Oak Park Elementary"
    ),
    Routine(
        id = "park-trip",
        firstTitle = "Drive to Park",
        firstIcon = "🌳",
        firstMins = 12,
        thenTitle = "Swings & Playground",
        thenIcon = "🛝",
        thenMins = 30,
        destStreet = "Cedar Creek Park"
    ),
    Routine(
        id = "going-home",
        firstTitle = "Heading Home",
        firstIcon = "🏡",
        firstMins = 20,
        thenTitle = "Snack & Quiet Time",
        thenIcon = "🍎",
        thenMins = 15,
        destStreet = "Maple Street Home"
    ),
    Routine(
        id = "grocery-pickup",
        firstTitle = "Grocery Curbside Wait",
        firstIcon = "🛒",
        firstMins = 8,
        thenTitle = "Pick a Car Sticker",
        thenIcon = "⭐",
        thenMins = 2,
        destStreet = "Market Square Lane"
    ),
    Routine(
        id = "drivethru-wait",
        firstTitle = "Drive-Thru Line",
        firstIcon = "🍟",
        firstMins = 5,
        thenTitle = "Eat in Car",
        thenIcon = "🧃",
        thenMins = 10,
        destStreet = "Main St Plaza"
    )
)
