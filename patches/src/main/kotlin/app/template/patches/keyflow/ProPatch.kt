package app.template.patches.keyflow

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.addFreeRegisters
import app.morphe.patcher.util.returnEarly
import app.template.patches.shared.Constants.COMPATIBILITY

@Suppress("unused")
val proPatch = patch(
    name = "Pro Unlock",
    description = "Forces all Pro features enabled and replaces the Google Play confirmation with a fake success, so ad surfaces never appear.",
    default = true
) {
    compatibleWith(COMPATIBILITY)

    execute {
        // Qx0hrwEl.H2h1oPylj(Context, RbDM1) -> fake successful Google Play confirmation.
        Fingerprint(
            definingClass = "LQx0hrwEl;",
            name = "H2h1oPylj",
            parameters = listOf("Landroid/content/Context;", "LRbDM1;"),
            returnType = "Ljava/lang/Object;"
        ).method.addInstructions(
            0,
            """
                new-instance v0, LKZr7ww;
                new-instance v1, LS19NG6;
                const-string v2, ""
                const-string v3, ""
                invoke-direct {v1, v2, v3}, LS19NG6;-><init>(Ljava/lang/String;Ljava/lang/String;)V
                invoke-direct {v0, v1}, LKZr7ww;-><init>(LS19NG6;)V
                return-object v0
            """.trimIndent()
        )

        // Qx0hrwEl.azrbON(Context, RbDM1) -> always take the Pro confirmation path (never the ad ticket).
        Fingerprint(
            definingClass = "LQx0hrwEl;",
            name = "azrbON",
            parameters = listOf("Landroid/content/Context;", "LRbDM1;"),
            returnType = "Ljava/lang/Object;"
        ).method.addInstructions(
            0,
            """
                invoke-virtual {p0, p1, p2}, LQx0hrwEl;->H2h1oPylj(Landroid/content/Context;LRbDM1;)Ljava/lang/Object;
                move-result-object p0
                return-object p0
            """.trimIndent()
        )

        // mx6nK.KEsFip5xs() -> no-op, prevents the timer from disabling Pro.
        Fingerprint(
            definingClass = "Lmx6nK;",
            name = "KEsFip5xs",
            parameters = listOf(),
            returnType = "V"
        ).method.returnEarly()

        // mx6nK.xBtItM() -> no-op, prevents disabling the active flag.
        Fingerprint(
            definingClass = "Lmx6nK;",
            name = "xBtItM",
            parameters = listOf(),
            returnType = "V"
        ).method.returnEarly()

        // mx6nK.xo0eU() -> force xBtItM = true and publish true to pX6SrQYLl.
        Fingerprint(
            definingClass = "Lmx6nK;",
            name = "xo0eU",
            parameters = listOf(),
            returnType = "V"
        ).method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                sput-boolean v0, Lmx6nK;->xBtItM:Z
                invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                move-result-object v0
                sget-object v1, Lmx6nK;->pX6SrQYLl:LEEoBErp8u;
                const/4 v2, 0x0
                invoke-virtual {v1, v2, v0}, LEEoBErp8u;->H2h1oPylj(Ljava/lang/Object;Ljava/lang/Object;)Z
                return-void
            """.trimIndent()
        )

        // mx6nK.pX6SrQYLl() -> always return a fake receipt, never null.
        // The getter has .locals 1 (only v0), so two free registers are added.
        val (pX6SrQYLlMethod, pX6SrQYLlAdded) = Fingerprint(
            definingClass = "Lmx6nK;",
            name = "pX6SrQYLl",
            parameters = listOf(),
            returnType = "LS19NG6;"
        ).method.addFreeRegisters(2)

        pX6SrQYLlMethod.addInstructions(
            pX6SrQYLlAdded,
            """
                new-instance v0, LS19NG6;
                const-string v1, ""
                const-string v2, ""
                invoke-direct {v0, v1, v2}, LS19NG6;-><init>(Ljava/lang/String;Ljava/lang/String;)V
                return-object v0
            """.trimIndent()
        )

        // Wd8lizD.xo0eU(DUL3JxrY) -> return true as a fallback so the ad ticket gate always passes.
        Fingerprint(
            definingClass = "LWd8lizD;",
            name = "xo0eU",
            parameters = listOf("LDUL3JxrY;"),
            returnType = "Z"
        ).method.returnEarly(true)

        // kjXTc.znlhtRTp() -> always return true (billing client ready), matching the mod.
        Fingerprint(
            definingClass = "LkjXTc;",
            name = "znlhtRTp",
            parameters = listOf(),
            returnType = "Z"
        ).method.returnEarly(true)

        // kjXTc.KEsFip5xs(Activity, E89G7LwNy) -> neuter billing client connect,
        // return QA6hx2MOZ with result code 0 (success), matching the mod.
        Fingerprint(
            definingClass = "LkjXTc;",
            name = "KEsFip5xs",
            parameters = listOf("Landroid/app/Activity;", "LE89G7LwNy;"),
            returnType = "LQA6hx2MOZ;"
        ).method.addInstructions(
            0,
            """
                new-instance v0, LQA6hx2MOZ;
                invoke-direct {v0}, Ljava/lang/Object;-><init>()V
                return-object v0
            """.trimIndent()
        )

        // uhgAYKl2b.xo0eU(DoCsWqGhG) -> always return true, bypassing mx6nK.azrbON check
        // which is initialized to false and never updated to true.
        Fingerprint(
            definingClass = "LuhgAYKl2b;",
            name = "xo0eU",
            parameters = listOf("LDoCsWqGhG;"),
            returnType = "Z"
        ).method.returnEarly(true)
    }
}
