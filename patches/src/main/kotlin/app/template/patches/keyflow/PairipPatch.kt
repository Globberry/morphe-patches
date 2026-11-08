package app.template.patches.keyflow

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.returnEarly
import app.template.patches.shared.Constants.COMPATIBILITY

@Suppress("unused")
val pairipPatch = patch(
    name = "PairIP License Check",
    description = "Neuters PairIP license verification so the app runs without a valid license.",
    default = true
) {
    compatibleWith(COMPATIBILITY)

    execute {
        Fingerprint(
            definingClass = "Lcom/pairip/licensecheck/LicenseClient;",
            name = "checkLicense",
            parameters = listOf("Landroid/content/Context;"),
            returnType = "V"
        ).method.returnEarly()
    }
}
