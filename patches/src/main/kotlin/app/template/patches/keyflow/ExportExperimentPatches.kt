package app.template.patches.keyflow

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.removeInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstructions
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
//
// IMPORTANT: an instruction must never be inserted *before* a label that is a
// branch target - that breaks the dex assembler.  All insertions below happen
// either at the very start of a method or right after a non-target
// instruction.
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
 * of each other: two patches that insert at the same index both survive, no
 * matter in which order they run.
 *
 * A snippet that does not compile (e.g. because it uses a register a format
 * cannot address) simply adds nothing, so nothing is ever corrupted.
 */
internal fun safeInsertInstructions(
    method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod,
    index: Int,
    smali: String
): Boolean {
    val impl = method.implementation ?: return false
    val before = impl.instructions.size
    method.addInstructions(index, smali)
    return impl.instructions.size > before
}

/**
 * Reports a patch that could not find its anchor.  This is deliberately NOT a
 * hard error: one broken patch must never abort the whole patch run, because
 * the point of this suite is to try many combinations.
 */
/**
 * Replaces the instruction at [index] with [smali].
 *
 * IMPORTANT: `replaceInstructions(index, list)` in the Morphe patcher removes
 * `list.size()` original instructions and then adds `list.size()` new ones -
 * so it silently swallows the instructions that follow.  Adding first and
 * removing the original afterwards replaces exactly one instruction and keeps
 * the instruction count (and therefore every branch offset) intact.
 */
internal fun safeReplaceInstructions(
    method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod,
    index: Int,
    smali: String
): Boolean {
    val count = smali.trim().lines().count { it.isNotBlank() }
    if (count == 0) return false
    val impl = method.implementation ?: return false
    val before = impl.instructions.size
    method.addInstructions(index, smali)
    val added = impl.instructions.size - before
    if (added != count) {
        // the snippet did not compile - roll back whatever made it in
        repeat(added) { method.removeInstruction(index) }
        return false
    }
    // the original instruction now sits right behind the new block
    method.removeInstruction(index + count)
    return true
}

/**
 * Renders an instruction back to smali so that it can be re-emitted after a
 * log statement.  Returns null for formats that are not supported.
 */
internal fun renderInstruction(instr: Instruction): String? {
    val op = instr.opcode.name
    return when {
        instr is TwoRegisterInstruction && instr is ReferenceInstruction ->
            "$op v${instr.registerA}, v${instr.registerB}, ${instr.reference}"
        instr is OneRegisterInstruction && instr is ReferenceInstruction ->
            "$op v${instr.registerA}, ${instr.reference}"
        instr is OneRegisterInstruction && instr is NarrowLiteralInstruction ->
            "$op v${instr.registerA}, ${instr.narrowLiteral}"
        instr is TwoRegisterInstruction ->
            "$op v${instr.registerA}, v${instr.registerB}"
        else -> null
    }
}

/**
 * Inserts a log statement in front of the instruction at [index].
 *
 * ONLY inserting is safe: `replaceInstructions(index, list)` in the Morphe
 * patcher removes `list.size()` original instructions and then adds
 * `list.size()` new ones, so it silently swallows the instructions that
 * follow the replaced one.  Inserting never removes anything.
 *
 * [reg] is the register the log uses, [valueReg] an optional second register
 * whose value is logged (e.g. the error message).
 */
internal fun insertLogBefore(
    method: app.morphe.patcher.util.proxy.mutableTypes.MutableMethod,
    index: Int,
    reg: Int,
    message: String,
    valueReg: Int? = null
): Boolean {
    val call = if (valueReg != null) {
        "invoke-static {v$reg, v$valueReg}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I"
    } else {
        "invoke-static {v$reg, v$reg}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I"
    }
    val smali = """
        const-string v$reg, "$message"
        $call
    """.trimIndent()
    return safeInsertInstructions(method, index, smali)
}

/**
 * Reports a patch that could not find its anchor.  This is deliberately NOT a
 * hard error: one broken patch must never abort the whole patch run, because
 * the point of this suite is to try many combinations.
 */
internal fun reportSkip(patchName: String, detail: String) {
    println("[Export] $patchName: SKIPPED - $detail")
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
//  [Export] 6  LCWcMAXf Error   replace the pre-export error message by ""
//  [Export] 7  LsvcJNUbK3 Allow sub-check of gate 1 -> always allow
//  [Export] 8  Diagnostics      full logcat trace of the export (tag KFPro)
//  [Export] 9  Toast            on-screen toast naming the blocker
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
            reportSkip("1 kts6dAU4 Gate", "Lkts6dAU4.NMGZ06(LXfNXj)Z not found")
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
            reportSkip("2 kts6dAU4 Throw", "no throw instruction found in Lkts6dAU4")
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
            reportSkip("3 y88yfJ73C Allow", "FALSE exit of LCtCUGg.y88yfJ73C not found")
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
            reportSkip("4 ZFJe6vx50 Skip", "abort branch after y88yfJ73C not found")
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
            reportSkip("5 kCg72Z4t State", "PRO_REQUIRED/ERROR state assignment not found")
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 6 - LCtCUGg.KPCmEBxs: "does the project use Pro features?".
//              Forcing it to false makes the second gate (y88yfJ73C) allow the
//              export and stops the Pro features from being reported.
//
//              NOTE: this used to blank out the message that
//              LCWcMAXf.c3oEd puts into the UI state, which wiped every
//              button label of the app.  That is why it is gone.
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp6CtCUGgProPatch = patch(
    name = "[Export] 6 CtCUGg Pro Features",
    description = "Pretend the project uses no Pro features (LCtCUGg.KPCmEBxs).",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LCtCUGg;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "KPCmEBxs") continue
                if (method.parameters.isNotEmpty()) continue
                if (method.returnType != "Z") continue
                method.returnEarly(false)
                patched++
            }
        }
        if (patched == 0) {
            reportSkip("6 CtCUGg Pro Features", "LCtCUGg.KPCmEBxs()Z not found")
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
            reportSkip("7 LsvcJNUbK3 Allow", "LsvcJNUbK3.NMGZ06(LUov9rzUD)Z not found")
        }
    }
}

// ---------------------------------------------------------------------------
// [Export] 8 - Diagnostics (logcat).
//
//     adb logcat -s KFPro
//
// This traces the *whole* export, not only the Pro gates, so it also shows
// when the export dies somewhere else:
//
//   KFPro: calling export function        <- the export coroutine started
//   KFPro: export function returned       <- the export function finished
//   KFPro: export SUCCEEDED               <- result was a success result
//   KFPro: export FAILED: <message>       <- result was an error result
//   KFPro: export result is a Pro result  <- result was a Pro result
//   KFPro: Pro feature detected           <- a Pro feature was found
//   KFPro: y88yfJ73C returned FALSE       <- the second gate blocked it
//
// `const-string` can only address v0..v15, so no register above 15 is used.
// ---------------------------------------------------------------------------
@Suppress("unused")
val exportExp8DiagnosticsPatch = patch(
    name = "[Export] 8 Diagnostics",
    description = "Log the whole export flow to logcat (tag KFPro). Read with: adb logcat -s KFPro",
    default = false
) {
    compatibleWith(COMPATIBILITY)
    execute {
        var patched = 0

        // ---- LP0JcGi9.cdo7kOuyS: the export function itself ------------------
        //      The first instruction writes its destination register, so the
        //      log can use that very register and the original instruction
        //      simply overwrites it again.
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LP0JcGi9;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "cdo7kOuyS") continue
                if (method.parameters.isEmpty()) continue
                if (method.parameters[0].type != "Landroid/content/Context;") continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                if (instrs.isEmpty()) continue
                if (!insertLogBefore(method, 0, 0, "KFPro: export function entered")) continue
                patched++
            }
        }

        // ---- LkCg72Z4t.invokeSuspend: the export coroutine ------------------
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LkCg72Z4t;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "invokeSuspend") continue
                if (method.parameters.size != 1) continue
                if (method.returnType != "Ljava/lang/Object;") continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                if (instrs.isEmpty()) continue

                // (index, smali) pairs - applied back to front so that the
                // indices stay valid while we work.
                val inserts = mutableListOf<Pair<Int, String>>()

                fun log(index: Int, reg: Int, message: String, valueReg: Int? = null) {
                    val call = if (valueReg != null) {
                        "invoke-static {v$reg, v$valueReg}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I"
                    } else {
                        "invoke-static {v$reg, v$reg}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I"
                    }
                    inserts.add(index to "const-string v$reg, \"$message\"\n$call")
                }

                // "export coroutine started" - the very first thing the
                // export coroutine does
                log(0, 12, "KFPro: export coroutine started")

                // (b) "export function returned" - the export function is done.
                //     v6 is dead at that point.
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.CHECK_CAST) continue
                    if (refOf(instrs[i]) != "LiGu8W;") continue
                    log(i + 1, 6, "KFPro: export function returned")
                    break
                }

                // (c) "export SUCCEEDED" - the result was a success result
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.INSTANCE_OF) continue
                    if (refOf(instrs[i]) != "LRBnKeZA7;") continue
                    for (j in i + 1 until instrs.size) {
                        if (instrs[j].opcode == Opcode.IF_EQZ) {
                            log(j + 1, 6, "KFPro: export SUCCEEDED")
                            break
                        }
                    }
                    break
                }

                // (d) "export FAILED" - the result was an error result
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.INSTANCE_OF) continue
                    if (refOf(instrs[i]) != "LR7XqhtD;") continue
                    for (j in i + 1 until instrs.size) {
                        if (instrs[j].opcode == Opcode.IF_EQZ) {
                            log(j + 1, 6, "KFPro: export FAILED")
                            break
                        }
                    }
                    break
                }

                // (e) the error message itself (v0 holds it at that point)
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.IGET_OBJECT) continue
                    val ref = refOf(instrs[i]) ?: continue
                    if (!ref.contains("LkCg72Z4t;->oMKv6w5")) continue
                    log(i + 1, 6, "KFPro", 0)
                    break
                }

                // (f) "export result is a Pro result" - the Pro result branch
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.INSTANCE_OF) continue
                    if (refOf(instrs[i]) != "Ly5XhdY0;") continue
                    for (j in i + 1 until instrs.size) {
                        if (instrs[j].opcode == Opcode.IF_EQZ) {
                            log(j + 1, 6, "KFPro: export result is a Pro result (Ly5XhdY0) -> PRO_REQUIRED")
                            break
                        }
                    }
                    break
                }

                // apply everything back to front so the indices stay valid
                for ((index, smali) in inserts.sortedByDescending { it.first }) {
                    if (!safeInsertInstructions(method, index, smali)) continue
                    patched++
                }
            }
        }

        // ---- Lkts6dAU4: the Pro feature collector ---------------------------
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "Lkts6dAU4;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (!method.name.startsWith("GX9XN3") && method.name != "c3oEd") continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                val where = if (method.name == "c3oEd") "c3oEd" else "GX9XN3ZIZ"
                val reg = if (method.name == "c3oEd") 14 else 0
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
                            const-string v$reg, "KFPro: Pro feature detected -> Pro-required exception ($where)"
                            invoke-static {v$reg, v$reg}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
                            """.trimIndent()
                        )
                    ) continue
                    patched++
                }
            }
        }

        // ---- LCtCUGg.y88yfJ73C: the second gate -----------------------------
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
                    var retIdx = -1
                    for (j in i + 1..minOf(i + 4, instrs.size - 1)) {
                        if (instrs[j].opcode == Opcode.RETURN) {
                            retIdx = j
                            break
                        }
                    }
                    if (retIdx < 0) continue
                    targets.add(i)
                }
                for (i in targets.reversed()) {
                    if (!safeInsertInstructions(
                            method,
                            i + 1,
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

        if (patched == 0) {
            reportSkip("8 Diagnostics", "no diagnostic anchor found")
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
            reportSkip("9 Toast", "Pro-result branch in LkCg72Z4t.invokeSuspend not found")
        }
    }
}





