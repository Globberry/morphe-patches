package app.template.patches.keyflow

import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.template.patches.shared.Constants.COMPATIBILITY
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.Opcode

/**
 * Keyflow 1.5.10 — Export Pro Gate Bypass (kCg72Z4t).
 *
 * When LP0JcGi9.cdo7kOuyS() returns a Ly5XhdY0 result (project uses Pro features),
 * kCg72Z4t.invokeSuspend sets the export state to PRO_REQUIRED, which causes the
 * export to fail with "Keyflow Pro required to pre-export this project".
 *
 * This patch changes the PRO_REQUIRED assignment to PLAYING so the export proceeds
 * as if Pro is active.
 */
@Suppress("unused")
val exportProGateBypassPatch = patch(
    name = "Keyflow Export Pro Gate Bypass",
    description = "Allows exporting projects that use Pro features by bypassing the Pro gate in the export pipeline.",
    default = true
) {
    compatibleWith(COMPATIBILITY)

    execute {
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LkCg72Z4t;") return@classDefForEach

            val mutable = mutableClassDefBy(classDef)

            // Find invokeSuspend(Object)Object — the coroutine that gates export on Pro
            mutable.methods.first { m ->
                m.name == "invokeSuspend" &&
                    m.parameters.size == 1 &&
                    m.parameters[0].type == "Ljava/lang/Object;" &&
                    m.returnType == "Ljava/lang/Object;"
            }.let { method ->
                val impl = method.implementation ?: return@let
                val instrs = impl.instructions

                // Find the sget-object ...->RdpTsF (PRO_REQUIRED) at :cond_8 and
                // replace it with ...->kwEINn3 (PLAYING) so the export proceeds.
                for (i in instrs.indices.reversed()) {
                    val instr = instrs[i]
                    if (instr.opcode == Opcode.SGET_OBJECT &&
                        instr.toString().contains("La82eAByx;->RdpTsF:La82eAByx;")) {
                        method.replaceInstruction(i,
                            "sget-object v0, La82eAByx;->kwEINn3:La82eAByx;")
                    }
                }
            }
        }
    }
}
