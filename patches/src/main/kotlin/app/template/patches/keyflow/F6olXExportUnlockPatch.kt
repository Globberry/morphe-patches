package app.template.patches.keyflow

import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.instruction.BranchInstruction
import com.android.tools.smali.dexlib2.Label

/**
 * Keyflow 1.5.10 — f6olX Premium Export Unlock.
 *
 * f6olX contains six premium-gate sites, each shaped like:
 *
 *     invoke-virtual ..., Lv5YtyUtD;->yMQp6hm(...)Ljava/lang/Object;
 *     move-result-object vX
 *     if-ne vX, vSentinel, :cond_Z        ; premium path  (result != not-premium sentinel)
 *     goto :goto_W                         ; failure path (returns not-premium sentinel)
 *
 * Morphe's existing "Pro Unlock" patch makes f6olX methods return early, but the export flow
 * still reaches one of the `goto :goto_W` failure sites that a 3-site manual patch misses.
 * Replacing every failure `goto :goto_W` with a jump to the premium `:cond_Z` label makes
 * every code path report Premium — including export.
 */
val f6olXExportUnlockPatch = patch(
    name = "Keyflow f6olX Export Unlock",
    description = "Bypasses all six f6olX premium-gate goto sites so Premium features (including export) work in Keyflow 1.5.10.",
    default = true
) {
    compatibleWith(COMPATIBILITY)

    execute {
        var patched = 0
        classDefForEach { classDef ->
            if (classDef.type != "Lf6olX;") return@classDefForEach

            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods) {
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                for (i in instrs.indices) {
                    val instr = instrs[i]
                    if (instr !is BranchInstruction || instr.opcode != Opcode.IF_NE) continue
                    if (i + 1 >= instrs.size) continue
                    val next = instrs[i + 1]
                    if (next !is BranchInstruction || next.opcode != Opcode.GOTO) continue

                    val ifNeTargets = instr.branchTargets ?: continue
                    val gotoTargets = next.branchTargets ?: continue
                    if (ifNeTargets.size != 1 || gotoTargets.size != 1) continue

                    val condLabel = ifNeTargets[0]
                    val gotoLabel = gotoTargets[0]
                    // Only bypass when if-ne jumps to the premium :cond_Z label but goto jumps
                    // elsewhere (the failure :goto_W path). Skip if they already agree.
                    if (condLabel != gotoLabel) {
                        next.branchTargets = arrayOf<Label>(condLabel)
                        patched++
                    }
                }
            }
        }

        if (patched == 0) {
            throw Error(
                "Keyflow f6olX Export Unlock: no premium-gate goto site found in f6olX — " +
                "expected Keyflow 1.5.10 classes4.dex. Patched $patched site(s)."
            )
        }
    }
}
