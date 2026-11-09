package app.template.patches.keyflow

import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.addFreeRegisters
import app.morphe.patcher.util.returnEarly
import app.template.patches.shared.Constants.COMPATIBILITY
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.ClassDef

@Suppress("unused")
val proPatch = patch(
    name = "Pro Unlock",
    description = "Forces all Pro features enabled and replaces the Google Play confirmation with a fake success, so ad surfaces never appear.",
    default = true
) {
    compatibleWith(COMPATIBILITY)

    execute {
        // ========================================================================
        // 1. Qx0hrwEl equivalent = Lf6olX
        //    H2h1oPylj(Context, RbDM1) -> NMGZ06(Context, ArrayList, LbJtamtc;)
        //    azrbON(Context, RbDM1) -> yMQp6hm(Context, LbJtamtc;)
        // ========================================================================
        classDefForEach { classDef: ClassDef ->
            if (classDef.type == "Lf6olX;") {
                val mutable = mutableClassDefBy(classDef)

                // NMGZ06(Context, ArrayList, LbJtamtc;) -> fake confirmation
                mutable.methods.first { m ->
                    m.name == "NMGZ06" &&
                        m.parameters.size == 3 &&
                        m.parameters[0].type == "Landroid/content/Context;" &&
                        m.parameters[1].type == "Ljava/util/ArrayList;" &&
                        m.parameters[2].type == "LbJtamtc;" &&
                        m.returnType == "Ljava/lang/Object;" &&
                        (m.accessFlags and AccessFlags.STATIC.value) != 0
                }.let { method ->
                    val (cloned, added) = method.addFreeRegisters(4)
                    cloned.addInstructions(added, """
                        new-instance v0, LKZr7ww;
                        new-instance v1, LS19NG6;
                        const-string v2, ""
                        const-string v3, ""
                        invoke-direct {v1, v2, v3}, LS19NG6;-><init>(Ljava/lang/String;Ljava/lang/String;)V
                        invoke-direct {v0, v1}, LKZr7ww;-><init>(LS19NG6;)V
                        return-object v0
                    """)
                }

                // yMQp6hm(Context, LbJtamtc;) -> always call NMGZ06
                mutable.methods.first { m ->
                    m.name == "yMQp6hm" &&
                        m.parameters.size == 2 &&
                        m.parameters[0].type == "Landroid/content/Context;" &&
                        m.parameters[1].type == "LbJtamtc;" &&
                        m.returnType == "Ljava/lang/Object;" &&
                        (m.accessFlags and AccessFlags.STATIC.value) == 0
                }.let { method ->
                    val (cloned, added) = method.addFreeRegisters(1)
                    cloned.addInstructions(added, """
                        invoke-virtual {p0, p1}, Lf6olX;->NMGZ06(Landroid/content/Context;Ljava/util/ArrayList;LbJtamtc;)Ljava/lang/Object;
                        move-result-object p0
                        return-object p0
                    """)
                }
            }
        }

        // ========================================================================
        // 2. mx6nK equivalent: class with static boolean field + time-checking methods
        // ========================================================================
        var mx6nK: ClassDef? = null
        classDefForEach { classDef: ClassDef ->
            val hasStaticBoolean = classDef.fields.any { f ->
                (f.accessFlags and AccessFlags.STATIC.value) != 0 && f.type == "Z"
            }
            val hasXo0eUVoid = classDef.methods.any { m ->
                m.name == "xo0eU" &&
                    m.parameters.isEmpty() &&
                    m.returnType == "V" &&
                    (m.accessFlags and AccessFlags.STATIC.value) != 0
            }
            val hasPx6srqyLlGetter = classDef.methods.any { m ->
                m.name == "pX6SrQYLl" &&
                    m.parameters.isEmpty() &&
                    m.returnType.startsWith("L") &&
                    m.returnType.endsWith(";") &&
                    (m.accessFlags and AccessFlags.STATIC.value) != 0
            }
            if (hasStaticBoolean && hasXo0eUVoid && hasPx6srqyLlGetter) {
                mx6nK = classDef
            }
        }

        if (mx6nK != null) {
            val mutableMx6nK = mutableClassDefBy(mx6nK!!)
            val mx6nKName = mutableMx6nK.type

            // Find xBtItM field (static boolean Z)
            val xBtItMField = mutableMx6nK.fields.firstOrNull { f ->
                (f.accessFlags and AccessFlags.STATIC.value) != 0 && f.type == "Z"
            }!!
            val xBtItMName = xBtItMField.name

            // Find pX6SrQYLl field (static, object type L...)
            val pX6SrQYLlField = mutableMx6nK.fields.firstOrNull { f ->
                (f.accessFlags and AccessFlags.STATIC.value) != 0 && f.type.startsWith("L") && f.type.endsWith(";")
            }!!
            val pX6SrQYLlName = pX6SrQYLlField.name
            val pX6SrQYLlType = pX6SrQYLlField.type

            // Find EEoBErp8u class (the type of pX6SrQYLl field)
            val eeroClass = mutableClassDefBy(pX6SrQYLlType)
            val eeroName = eeroClass.type

            // Find H2h1oPylj(Object, Object)Z on EEoBErp8u
            val h2h1oPyljMethod = eeroClass.methods.first { m ->
                m.name == "H2h1oPylj" &&
                    m.parameters.size == 2 &&
                    m.parameters[0].type == "Ljava/lang/Object;" &&
                    m.parameters[1].type == "Ljava/lang/Object;" &&
                    m.returnType == "Z"
            }

            // mx6nK.KEsFip5xs()V -> returnEarly()
            mutableMx6nK.methods.first { m ->
                m.name == "KEsFip5xs" && m.parameters.isEmpty() && m.returnType == "V" && (m.accessFlags and AccessFlags.STATIC.value) != 0
            }.returnEarly()

            // mx6nK.xBtItM()V -> returnEarly()
            mutableMx6nK.methods.first { m ->
                m.name == "xBtItM" && m.parameters.isEmpty() && m.returnType == "V" && (m.accessFlags and AccessFlags.STATIC.value) != 0
            }.returnEarly()

            // mx6nK.xo0eU()V -> force xBtItM=true, publish true to pX6SrQYLl
            val (xo0eUCloned, xo0eUAdded) = mutableMx6nK.methods.first { m ->
                m.name == "xo0eU" && m.parameters.isEmpty() && m.returnType == "V" && (m.accessFlags and AccessFlags.STATIC.value) != 0
            }.addFreeRegisters(3)
            xo0eUCloned.addInstructions(xo0eUAdded, """
                const/4 v0, 0x1
                sput-boolean v0, $mx6nKName->$xBtItMName:Z
                invoke-static {v0}, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                move-result-object v0
                sget-object v1, $mx6nKName->$pX6SrQYLlName:$pX6SrQYLlType
                const/4 v2, 0x0
                invoke-virtual {v1, v2, v0}, $eeroName->H2h1oPylj(Ljava/lang/Object;Ljava/lang/Object;)Z
                return-void
            """)

            // mx6nK.pX6SrQYLl()LS19NG6; -> return fake S19NG6("", "")
            val s19ng6Name = findClassByName("LS19NG6;")
            val (getterCloned, getterAdded) = mutableMx6nK.methods.first { m ->
                m.name == "pX6SrQYLl" && m.parameters.isEmpty() && m.returnType.startsWith("L") && m.returnType.endsWith(";") && (m.accessFlags and AccessFlags.STATIC.value) != 0
            }.addFreeRegisters(3)
            getterCloned.addInstructions(getterAdded, """
                new-instance v0, $s19ng6Name
                const-string v1, ""
                const-string v2, ""
                invoke-direct {v0, v1, v2}, $s19ng6Name-><init>(Ljava/lang/String;Ljava/lang/String;)V
                return-object v0
            """)
        }

        // ========================================================================
        // 3. Wd8lizD equivalent: class with xo0eU(someType)Z method
        // ========================================================================
        classDefForEach { classDef: ClassDef ->
            val hasXo0eU = classDef.methods.any { m ->
                m.name == "xo0eU" &&
                    m.parameters.size == 1 &&
                    m.parameters[0].type.startsWith("L") &&
                    m.parameters[0].type.endsWith(";") &&
                    m.returnType == "Z"
            }
            if (hasXo0eU) {
                val mutable = mutableClassDefBy(classDef)
                mutable.methods.first { m ->
                    m.name == "xo0eU" &&
                        m.parameters.size == 1 &&
                        m.parameters[0].type.startsWith("L") &&
                        m.parameters[0].type.endsWith(";") &&
                        m.returnType == "Z"
                }.returnEarly(true)
            }
        }

        // ========================================================================
        // 4. kjXTc equivalent: class with znlhtRTp()Z and KEsFip5xs(Activity, ...)
        // ========================================================================
        var kjxtc: ClassDef? = null
        classDefForEach { classDef: ClassDef ->
            val hasZnlhtRTp = classDef.methods.any { m ->
                m.name == "znlhtRTp" && m.parameters.isEmpty() && m.returnType == "Z"
            }
            val hasKEsFip5xs = classDef.methods.any { m ->
                m.name == "KEsFip5xs" &&
                    m.parameters.size == 2 &&
                    m.parameters[0].type == "Landroid/app/Activity;" &&
                    m.returnType.startsWith("L") &&
                    m.returnType.endsWith(";") &&
                    (m.accessFlags and AccessFlags.STATIC.value) != 0
            }
            if (hasZnlhtRTp && hasKEsFip5xs) {
                kjxtc = classDef
            }
        }

        if (kjxtc != null) {
            val mutableKjxtc = mutableClassDefBy(kjxtc!!)

            // kjXTc.znlhtRTp()Z -> returnEarly(true)
            mutableKjxtc.methods.first { m ->
                m.name == "znlhtRTp" && m.parameters.isEmpty() && m.returnType == "Z"
            }.returnEarly(true)

            // kjXTc.KEsFip5xs(Activity, ...) -> return new QA6hx2MOZ()
            val qa6hx2mozName = findClassByName("LQA6hx2MOZ;")
            mutableKjxtc.methods.first { m ->
                m.name == "KEsFip5xs" &&
                    m.parameters.size == 2 &&
                    m.parameters[0].type == "Landroid/app/Activity;" &&
                    m.returnType.startsWith("L") &&
                    m.returnType.endsWith(";") &&
                    (m.accessFlags and AccessFlags.STATIC.value) != 0
            }.let { method ->
                val (cloned, added) = method.addFreeRegisters(2)
                cloned.addInstructions(added, """
                    new-instance v0, $qa6hx2mozName
                    invoke-direct {v0}, Ljava/lang/Object;-><init>()V
                    return-object v0
                """)
            }
        }

        // ========================================================================
        // 5. uhgAYKl2b equivalent: class with xo0eU(DoCsWqGhG)Z
        // ========================================================================
        classDefForEach { classDef: ClassDef ->
            val hasXo0eUDocs = classDef.methods.any { m ->
                m.name == "xo0eU" &&
                    m.parameters.size == 1 &&
                    m.parameters[0].type.startsWith("L") &&
                    m.returnType == "Z"
            }
            if (hasXo0eUDocs) {
                val mutable = mutableClassDefBy(classDef)
                mutable.methods.first { m ->
                    m.name == "xo0eU" &&
                        m.parameters.size == 1 &&
                        m.parameters[0].type.startsWith("L") &&
                        m.returnType == "Z"
                }.returnEarly(true)
            }
        }
    }
}

/** Find a class by its exact type name (e.g., "LS19NG6;", "LKZr7ww;"). */
private fun findClassByName(namePattern: String): String {
    var found: String? = null
    classDefForEach { classDef: ClassDef ->
        if (classDef.type == namePattern) {
            found = classDef.type
        }
    }
    return found ?: namePattern
}
