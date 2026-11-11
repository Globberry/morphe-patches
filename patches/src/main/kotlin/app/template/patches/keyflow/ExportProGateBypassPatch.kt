package app.template.patches.keyflow

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.returnEarly
import app.template.patches.shared.Constants.COMPATIBILITY
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef

/**
 * Keyflow 1.5.10 - Export Pro Gate Bypass (all-in-one).
 *
 * Combines the parts of the [Export] experiment suite that make up the most
 * likely complete fix:
 *
 *  1. Lkts6dAU4.NMGZ06  -> always allow  (the real Pro gate)
 *  2. Lkts6dAU4         -> never throw the Pro-required exception
 *  3. LCtCUGg.y88yfJ73C -> always allow  (the second gate)
 *  4. LZFJe6vx50.invoke -> never take the export-cancel branch
 *  5. LkCg72Z4t         -> publish PLAYING instead of PRO_REQUIRED / ERROR
 *  6. LP0JcGi9.MeOIJuj  -> accept the paid-export receipt check
 *
 * Enabled by default; every step is also available as an individual [Export]
 * patch for troubleshooting.
 */
@Suppress("unused")
val exportProGateBypassPatch = patch(
    name = "Keyflow Export Pro Gate Bypass",
    description = "Allows exporting projects that use Pro features by bypassing the Pro purchase checks in the export pipeline.",
    default = true
) {
    compatibleWith(COMPATIBILITY)

    execute {
        var patched = 0

        // 1 + 2. The main Pro gate and the Pro-required exception
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "Lkts6dAU4;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name == "NMGZ06" &&
                    method.parameters.size == 1 &&
                    method.parameters[0].type == "LXfNXj;" &&
                    method.returnType == "Z"
                ) {
                    method.returnEarly(true)
                    patched++
                    continue
                }
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

        // 3. The second gate
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

        // 4. Never take the export-cancel branch
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

        // 5. Publish PLAYING instead of PRO_REQUIRED / ERROR
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

        // 6. Accept the paid-export purchase check (Export screen path)
        classDefForEach { classDef: ClassDef ->
            if (classDef.type != "LP0JcGi9;") return@classDefForEach
            val mutable = mutableClassDefBy(classDef)
            for (method in mutable.methods.toList()) {
                if (method.name != "MeOIJuj") continue
                if (method.parameters.size != 2) continue
                if (method.parameters[0].type != "Landroid/content/Context;") continue
                if (method.returnType != "Ljava/lang/Object;") continue
                val impl = method.implementation ?: continue
                val instrs = impl.instructions
                // There is exactly one "new-instance LilvEt;" in the app; the
                // branch right before it is the receipt-verification result.
                for (i in instrs.indices) {
                    if (instrs[i].opcode != Opcode.NEW_INSTANCE) continue
                    if (refOf(instrs[i]) != "LilvEt;") continue
                    val branch = i - 1
                    if (branch < 0) continue
                    if (instrs[branch].opcode != Opcode.IF_EQZ) continue
                    method.replaceInstruction(branch, "nop")
                    patched++
                }
            }
        }

        if (patched == 0) {
            reportSkip("Keyflow Export Pro Gate Bypass", "nothing was patched")
        }
    }
}
