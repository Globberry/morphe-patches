package app.template.patches.keyflow

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.addFreeRegisters
import app.morphe.patcher.util.returnEarly
import app.template.patches.shared.Constants.COMPATIBILITY
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

// ---------------------------------------------------------------------------
// helpers
//
// NOTE: the instruction objects handed out by the Morphe patcher are
// `BuilderInstruction`s whose `toString()` is the default Object one, so all
// matching has to go through the typed dexlib2 interfaces.
// ---------------------------------------------------------------------------

/** Destination register of a single-register instruction, or null. */
internal fun destRegister(instr: Instruction): Int? =
    (instr as? OneRegisterInstruction)?.registerA

/** Source register of a two-register instruction, or null. */
internal fun srcRegister(instr: Instruction): Int? =
    (instr as? TwoRegisterInstruction)?.registerB

/** Literal value of a `const/4` instruction, or null. */
internal fun const4Literal(instr: Instruction): Int? {
    if (instr.opcode != Opcode.CONST_4) return null
    return (instr as? NarrowLiteralInstruction)?.narrowLiteral
}

/**
 * Printable reference of an instruction (type / field / method reference).
 * e.g. "Ly5XhdY0;", "La82eAByx;->RdpTsF:La82eAByx;", "LCtCUGg;->y88yfJ73C(IZ)Z"
 */
internal fun refOf(instr: Instruction): String? =
    (instr as? ReferenceInstruction)?.reference?.toString()

/** Absolute register number of the first parameter of [method]. */
internal fun firstParamRegister(method: Method): Int {
    var slots = 0
    for (p in method.parameters) {
        slots += if (p.type == "J" || p.type == "D") 2 else 1
    }
    val total = method.implementation?.registerCount ?: return -1
    return total - slots
}

/**
 * Inserts [smali] at [index] (before the instruction that is currently there).
 *
 * Only inserting - never removing - keeps the individual patches independent
 * of each other: two patches that insert at the same index both survive,
 * no matter in which order they run.
 *
 * A snippet that does not compile (e.g. because it uses a register a format
 * cannot address) simply adds nothing, and anything that was added is
 * rolled back.
 */
internal fun safeInsertInstructions(
    method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod,
    index: Int,
    smali: String
): Boolean {
    val impl = method.implementation ?: return false
    val before = impl.instructions.size
    method.addInstructions(index, smali)
    // a snippet that does not compile simply adds nothing
    return impl.instructions.size > before
}

// ---------------------------------------------------------------------------
// Keyflow 1.5.10 - Export Pro Gate: EXPERIMENTAL PATCH SUITE.
//
// Every patch here is independent and individually toggleable so that
// different combinations can be tested. All of them are disabled by default.
//
// How the Pro gate actually works in 1.5.10
// -----------------------------------------
//
//  export coroutine (LkCg72Z4t.invokeSuspend)
//    +-- LP0JcGi9.cdo7kOuyS(...)             <- performs the real export
//         +-- Lkts6dAU4.GX9XN3ZIZ / c3oEd   <- collects the Pro features used
//              +-- Lkts6dAU4.NMGZ06(LXfNXj)Z  <- THE MAIN GATE
//                   +-- this.GX9XN3IZ callback (the stored Pro receipt)
//                   +-- LsvcJNUbK3.NMGZ06(this.c3oEd)
//                   +-- LuHQuORIu.eFUbX       (the "pro_active" flag)
//              +-- if the gate says "no" -> throw LwNKvkk7 (Pro required)
//
//  LwNKvkk7 is caught inside P0JcGi9.cdo7kOuyS and converted into a
//  Ly5XhdY0 ("Pro result") object, which LkCg72Z4t then turns into the
//  La82eAByx.RdpTsF (PRO_REQUIRED) export state -> the dialog closes silently.
//
//  LCtCUGg.y88yfJ73C is a second, independent gate; when it returns false
//  LZFJe6vx50.invoke bails out of the export UI.
//
// The patches
// -----------
//
//  [Export] 1  kts6dAU4 Gate    * primary * the real Pro gate -> always allow
//  [Export] 2  kts6dAU4 Throw   * primary * don't throw the Pro exception
//  [Export] 3  y88yfJ73C Allow  the second gate -> always allow
//  [Export] 4  ZFJe6vx50 Skip   don't take the export-abort branch
//  [Export] 5  kCg72Z4t State   PRO_REQUIRED / ERROR -> PLAYING
//  [Export] 6  LCWcMAXf Error   drop the pre-export error message
//  [Export] 7  LsvcJNUbK3 Allow sub-check of gate 1 -> always allow
//  [Export] 8  Diagnostics      logcat tracing of every gate (tag "KFPro")
//  [Export] 9  Toast            on-screen toast naming the blocker
//  [Export] 10 LP4eSlPPu Allow  the per-Pro-feature check -> always allow
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// [Export] 1 - Lkts6dAU4.NMGZ06: the real "is this Pro feature allowed?" gate.
//              Forcing it to true stops the Pro-required exception from ever
//              being thrown, so the export simply runs to completion.
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp1Kts6dAU4GatePatch = patch(
    name = "[Export] 1 kts6dAU4 Gate",
    description = "Always allow Pro features in the export Pro gate (Lkts6dAU4.NMGZ06).",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "Lkts6dAU4;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "NMGZ06") continue
                if (method.parameters.size != 1) continue
                if (method.parameters[0].type != "LXfNXj;") continue
                if (method.returnType != "Z") continue
                method.returnEarly(true)
                patched++
            }
        }
        if (patched == 0) {
            throw Error("[Export] 1 kts6dAU4 Gate: Lkts6dAU4.NMGZ06(LXfNXj)Z not found.")
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 2 - Lkts6dAU4: never throw the "Pro required" exception.
//              This is the literal "reroute premium export -> normal export":
//              the Pro features are simply ignored and the export continues.
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp2Kts6dAU4ThrowPatch = patch(
    name = "[Export] 2 kts6dAU4 Throw",
    description = "Do not throw the Pro-required exception (LwNKvkk7) during export.",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "Lkts6dAU4;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (!method.name.startsWith("GX9XN3") && method.name != "c3oEd") continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                val throwIndices = instrs.indices.filter { instrs[it].opcode == Opcode.THROW }
                for (i in throwIndices.reversed()) {
                    method.replaceInstruction(i, "return-void")
                    patched++
                }
            }
        }
        if (patched == 0) {
            throw Error("[Export] 2 kts6dAU4 Throw: no throw instruction found in Lkts6dAU4.")
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 3 - LCtCUGg.y88yfJ73C: the second gate. It returns FALSE when the
//              export must be aborted. Force it to always return TRUE.
//
//              The method has two exits:
//                  const/4 v1, 0   /  return v1     <- FALSE  (blocked)
//                  return v0                         <- TRUE
//
//              We insert "const/4 v1, 1" right before the `return` of the
//              FALSE exit, so the exit returns 1 instead of 0. The
//              `const/4 v1, 0` marker stays intact (the Diagnostics patch
//              anchors on it).
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp3Y88yfJ73CPatch = patch(
    name = "[Export] 3 y88yfJ73C Allow",
    description = "Always let the export pass the LCtCUGg.y88yfJ73C abort check.",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LCtCUGg;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "y88yfJ73C") continue
                if (method.parameters.size != 2) continue
                if (method.parameters[0].type != "I" || method.parameters[1].type != "Z") continue
                if (method.returnType != "Z") continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                for (i in instrs.indices) {
                    if (const4Literal(instrs[i]) != 0) continue
                    val reg = destRegister(instrs[i]) ?: continue
                    // The matching "return" may be a couple of instructions
                    // away (the Diagnostics patch inserts a log in between).
                    var retIdx = -1
                    for (j in i + 1..minOf(i + 4, instrs.size - 1)) {
                        if (instrs[j].opcode == Opcode.RETURN) {
                            retIdx = j
                            break
                        }
                    }
                    if (retIdx < 0) continue
                    if (!safeInsertInstructions(
                            method,
                            retIdx,
                            """
                            const/4 v$reg, 0x1
                            """.trimIndent()
                        )
                    ) continue
                    patched++
                }
            }
        }
        if (patched == 0) {
            throw Error("[Export] 3 y88yfJ73C Allow: FALSE exit of LCtCUGg.y88yfJ73C not found.")
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 4 - LZFJe6vx50.invoke: the branch that bails out of the export when
//              y88yfJ73C() returns false.
//
//                invoke-virtual {v1, v5, v2}, LCtCUGg;->y88yfJ73C(IZ)Z
//                move-result v2
//                if-eqz v2, :cond_8      <- this one
//
//              We anchor on the invoke (not on the label name, which is not
//              stable) and replace the branch two instructions later with nop.
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp4ZFJe6vx50Patch = patch(
    name = "[Export] 4 ZFJe6vx50 Skip",
    description = "Never take the export-abort branch in LZFJe6vx50.invoke.",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LZFJe6vx50;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "invoke") continue
                if (method.parameters.size != 2) continue
                if (method.returnType != "Ljava/lang/Object;") continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                val targets = mutableListOf<Int>()
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.INVOKE_VIRTUAL) continue
                    val ref = refOf(instrs[i]) ?: continue
                    if (!ref.contains("y88yfJ73C")) continue
                    if (i + 2 >= instrs.size) continue
                    if (instrs[i + 1].opcode != Opcode.MOVE_RESULT) continue
                    if (instrs[i + 2].opcode != Opcode.IF_EQZ) continue
                    targets.add(i + 2)
                }
                for (i in targets.reversed()) {
                    method.replaceInstruction(i, "nop")
                    patched++
                }
            }
        }
        if (patched == 0) {
            throw Error("[Export] 4 ZFJe6vx50 Skip: abort branch after y88yfJ73C not found.")
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 5 - LkCg72Z4t.invokeSuspend: never publish the PRO_REQUIRED (or
//              ERROR) export state. The UI state machine has no
//              PRO_REQUIRED -> ok transition, so the export dialog just closes.
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp5KCg72Z4tStatePatch = patch(
    name = "[Export] 5 kCg72Z4t State",
    description = "Publish PLAYING instead of PRO_REQUIRED/ERROR as export state.",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LkCg72Z4t;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "invokeSuspend") continue
                if (method.parameters.size != 1) continue
                if (method.returnType != "Ljava/lang/Object;") continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                val targets = mutableListOf<Int>()
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.SGET_OBJECT) continue
                    val ref = refOf(instrs[i]) ?: continue
                    if (!ref.contains("La82eAByx;->RdpTsF:") &&
                        !ref.contains("La82eAByx;->Jp4lVJ:")
                    ) continue
                    targets.add(i)
                }
                for (i in targets.reversed()) {
                    val reg = destRegister(instrs[i]) ?: continue
                    method.replaceInstruction(
                        i,
                        "sget-object v$reg, La82eAByx;->kwEINn3:La82eAByx;"
                    )
                    patched++
                }
            }
        }
        if (patched == 0) {
            throw Error("[Export] 5 kCg72Z4t State: PRO_REQUIRED/ERROR state assignment not found.")
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 6 - LCWcMAXf.c3oEd: the pre-export step receives the error message
//              as its first argument and forwards it to LCtCUGg.cdo7kOuyS(),
//              which then flips the export state into "error". Drop it.
//
//                  move-object/from16 v4, p0        <- replace with null
//                  invoke-virtual {v0, v4}, LCtCUGg;->cdo7kOuyS(Ljava/lang/Object;)Z
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp6LCWcMAXfErrorPatch = patch(
    name = "[Export] 6 LCWcMAXf Error",
    description = "Pass a null error message into the pre-export step.",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        var lastError = "<none>"
        val errors = mutableListOf<String>()
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LCWcMAXf;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "c3oEd") continue
                if (method.returnType != "V") continue
                if (method.parameters.isEmpty()) continue
                if (method.parameters[0].type != "Ljava/lang/String;") continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                val p0 = firstParamRegister(method)
                val targets = mutableListOf<Int>()
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.MOVE_OBJECT_FROM16) continue
                    if (srcRegister(instrs[i]) != p0) continue
                    targets.add(i)
                }
                for (i in targets.reversed()) {
                    val reg = destRegister(instrs[i]) ?: continue
                    // `const/4` can only address v0..v15, fall back to
                    // `const/16` for anything above that.
                    val candidates = listOf("const/4 v$reg, 0x0", "const/16 v$reg, 0x0")
                    var done = false
                    for (candidate in candidates) {
                        try {
                            method.replaceInstruction(i, candidate)
                            done = true
                            break
                        } catch (t: Throwable) {
                            lastError = "$candidate -> $t"
                        }
                    }
                    if (done) patched++
                    else errors.add("idx=$i reg=$reg p0=$p0 : $lastError")
                }
            }
        }
        if (patched == 0) {
            throw Error(
                "[Export] 6 LCWcMAXf Error: error-message argument not found in LCWcMAXf.c3oEd. $errors"
            )
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 7 - LsvcJNUbK3.NMGZ06: one of the three sub-checks of the main
//              gate. Force it to true as well.
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp7LsvcJNUbK3Patch = patch(
    name = "[Export] 7 LsvcJNUbK3 Allow",
    description = "Always allow the LsvcJNUbK3 sub-check of the export Pro gate.",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LsvcJNUbK3;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "NMGZ06") continue
                if (method.parameters.size != 1) continue
                if (method.parameters[0].type != "LUov9rzUD;") continue
                if (method.returnType != "Z") continue
                method.returnEarly(true)
                patched++
            }
        }
        if (patched == 0) {
            throw Error("[Export] 7 LsvcJNUbK3 Allow: LsvcJNUbK3.NMGZ06(LUov9rzUD)Z not found.")
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 8 - Diagnostics (logcat).
//
//     adb logcat -s KFPro
//
// Every log line uses registers that are provably dead at that point and
// restores them afterwards so the patched bytecode stays verifiable.
// `const-string` can only address v0..v15, so no register above 15 is used.
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp8DiagnosticsPatch = patch(
    name = "[Export] 8 Diagnostics",
    description = "Log every export Pro gate decision to logcat (tag KFPro). Read with: adb logcat -s KFPro",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0

        // 8a - Lkts6dAU4.GX9XN3ZIZ: the Pro-required exception is created
        //      here. v0 is dead at that point (last use is the IdxtyejAx call).
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "Lkts6dAU4;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (!method.name.startsWith("GX9XN3")) continue
                if (method.parameters.size != 3) continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                val targets = mutableListOf<Int>()
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.NEW_INSTANCE) continue
                    if (refOf(instrs[i]) != "LwNKvkk7;") continue
                    targets.add(i + 1)
                }
                for (i in targets.reversed()) {
                    if (!safeInsertInstructions(
                            method,
                            i,
                            """
                            const-string v0, "KFPro: Pro feature detected -> Pro-required exception (GX9XN3ZIZ)"
                            invoke-static {v0, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
                            """.trimIndent()
                        )
                    ) continue
                    patched++
                }
            }
        }

        // 8b - Lkts6dAU4.c3oEd: the second place where the exception is made.
        //      v14 is dead at that point.
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "Lkts6dAU4;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "c3oEd") continue
                if (method.parameters.size != 1) continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                val targets = mutableListOf<Int>()
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.NEW_INSTANCE) continue
                    if (refOf(instrs[i]) != "LwNKvkk7;") continue
                    targets.add(i + 1)
                }
                for (i in targets.reversed()) {
                    if (!safeInsertInstructions(
                            method,
                            i,
                            """
                            const-string v14, "KFPro: Pro feature detected -> Pro-required exception (c3oEd)"
                            invoke-static {v14, v14}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
                            """.trimIndent()
                        )
                    ) continue
                    patched++
                }
            }
        }

        // 8c - LCtCUGg.y88yfJ73C: the second gate. The FALSE exit is
        //      "const/4 v1, 0". v0 holds the int 1 here; it is restored
        //      afterwards so the register type stays consistent.
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LCtCUGg;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "y88yfJ73C") continue
                if (method.parameters.size != 2) continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                val targets = mutableListOf<Int>()
                for (i in instrs.indices) {
                    if (const4Literal(instrs[i]) != 0) continue
                    val reg = destRegister(instrs[i]) ?: continue
                    // the matching "return" may be a couple of instructions
                    // away (the "Allow" patch inserts a const in between)
                    var retIdx = -1
                    for (j in i + 1..minOf(i + 4, instrs.size - 1)) {
                        if (instrs[j].opcode == Opcode.RETURN) {
                            retIdx = j
                            break
                        }
                    }
                    if (retIdx < 0) continue
                    targets.add(i + 1)
                }
                for (i in targets.reversed()) {
                    if (!safeInsertInstructions(
                            method,
                            i,
                            """
                            const-string v0, "KFPro: y88yfJ73C returned FALSE -> export blocked by this gate"
                            invoke-static {v0, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
                            const/4 v0, 0x1
                            """.trimIndent()
                        )
                    ) continue
                    patched++
                }
            }
        }

        // 8d - LkCg72Z4t.invokeSuspend: the app has decided that the export
        //      result is a Pro result. v6 is dead at that point.
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LkCg72Z4t;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "invokeSuspend") continue
                if (method.parameters.size != 1) continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                var branchIdx = -1
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.INSTANCE_OF) continue
                    if (refOf(instrs[i]) != "Ly5XhdY0;") continue
                    // the branch that decides whether we are in the Pro branch
                    for (j in i + 1 until instrs.size) {
                        if (instrs[j].opcode == Opcode.IF_EQZ) {
                            branchIdx = j
                            break
                        }
                    }
                    break
                }
                if (branchIdx < 0) continue
                if (!safeInsertInstructions(
                        method,
                        branchIdx + 1,
                        """
                        const-string v6, "KFPro: export result is a Pro result (Ly5XhdY0) -> PRO_REQUIRED"
                        invoke-static {v6, v6}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
                        """.trimIndent()
                    )
                ) continue
                patched++
            }
        }

        if (patched == 0) {
            throw Error("[Export] 8 Diagnostics: no diagnostic anchor found.")
        }
        if (patched < 4) {
            throw Error(
                "[Export] 8 Diagnostics: only $patched of 4 diagnostic sites were instrumented."
            )
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 9 - Toast.
//
// Shows an on-screen toast naming the blocker when the export is cancelled
// because of a Pro feature. v6/v7/v8 are dead at that point and the Context
// comes from the export composable's own Context field.
//
// NOTE: this is a separate patch on purpose - if the toast turns out to be
// problematic on your device you can simply untick it.
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp9ToastPatch = patch(
    name = "[Export] 9 Toast",
    description = "Show a toast naming the reason the export was cancelled.",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LkCg72Z4t;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "invokeSuspend") continue
                if (method.parameters.size != 1) continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                var branchIdx = -1
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.INSTANCE_OF) continue
                    if (refOf(instrs[i]) != "Ly5XhdY0;") continue
                    for (j in i + 1 until instrs.size) {
                        if (instrs[j].opcode == Opcode.IF_EQZ) {
                            branchIdx = j
                            break
                        }
                    }
                    break
                }
                if (branchIdx < 0) continue
                if (!safeInsertInstructions(
                        method,
                        branchIdx + 1,
                        """
                        iget-object v6, v12, LkCg72Z4t;->Jp4lVJ:Landroid/content/Context;
                        const-string v7, "KFPro: export cancelled - Pro feature detected (see logcat KFPro)"
                        const/4 v8, 0x0
                        invoke-static {v6, v7, v8}, LEolhBv;->c3oEd(Landroid/content/Context;Ljava/lang/String;Z)V
                        """.trimIndent()
                    )
                ) continue
                patched++
            }
        }
        if (patched == 0) {
            throw Error("[Export] 9 Toast: Pro-result branch in LkCg72Z4t.invokeSuspend not found.")
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 10 - LP4eSlPPu: the per-Pro-feature check. It is the callback the
//               main gate invokes with the stored Pro receipt; forcing it to
//               return TRUE means "this individual Pro feature is allowed".
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp10LP4eSlPPuPatch = patch(
    name = "[Export] 10 LP4eSlPPu Allow",
    description = "Always allow an individual Pro feature (LP4eSlPPu.invoke).",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LP4eSlPPu;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "invoke") continue
                if (method.parameters.size != 1) continue
                if (method.parameters[0].type != "Ljava/lang/Object;") continue
                if (method.returnType != "Ljava/lang/Object;") continue
                val (cloned, added) = method.addFreeRegisters(1)
                cloned.addInstructions(
                    added,
                    """
                    const/4 v0, 0x1
                    invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                    move-result-object v0
                    return-object v0
                    """.trimIndent()
                )
                patched++
            }
        }
        if (patched == 0) {
            throw Error("[Export] 10 LP4eSlPPu Allow: LP4eSlPPu.invoke(Object)Object not found.")
        }
    }
}


