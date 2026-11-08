package app.template.patches.keyflow

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY

private const val CERT_BASE64 =
    "MIIDczCCAlugAwIBAgIIO0ztybmwZ7IwDQYJKoZIhvcNAQEMBQAwZzELMAkGA1UEBhMCSUQxEDAOBgNVBAgTB1Vua25vd24xEDAOBgNVBAcTB1Vua25vd24xEDAOBgNVBAoTB0tleWZsb3cxEDAOBgNVBAsTB0tleWZsb3cxEDAOBgNVBAMTB0tleWZsb3cwIBcNMjYwODAyMDYyMTQ4WhgPMjA1MzEyMTgwNjIxNDhaMGcxCzAJBgNVBAYTAklEMRAwDgYDVQQIEwdVbmtub3duMRAwDgYDVQQHEwdVbmtub3duMRAwDgYDVQQKEwdLZXlmbG93MRAwDgYDVQQLEwdLZXlmbG93MRAwDgYDVQQDEwdLZXlmbG93MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqcNL4DjaenzRBAzUA/LttRA7TUq6pJ4K20aBhwJG1u1ChzeDgc5xW5mm71wH+DEGbJ6iN5mno+jJw2UWSzE4Z7cchiRc79kLRndtu/NvxL1/JNjVTUng1+vtk79RwG/uOQ4Yt6wpTili0F9YTPVvdQALQNMKl9ySSHUzwz4oDrXZmuacoj386Vhp+1nx4NxW/Vso2I1bwWTDaFJ0Pn05+PlgIs4H1i9Fg59JnyztL5m8hyNLn9E8iV7ieGKPL2Gj8iwUW8Ces12MBsbo5TKIpeykBlvRJUuKvCW4yErF7XeP8gFRHtZXJ93vS6D1gCa/kFmjfFic60KhHS8x0afXwwIDAQABoyEwHzAdBgNVHQ4EFgQUFY+b2FXXvhJHdpSE/R0PBVp2scEwDQYJKoZIhvcNAQEMBQADggEBAECWizRUzgj0s1UCMc1PPGw4vdBNfTOaeP7kpv3scX/w0/95zl7V7KzfociQAmi2G+UUf4Nyz7H+aCNihjWPslP3rSMkqpdnPWBOM70XUaZgcoAycnf6VvqIsAV5cd1XqmhRPrk0mvKK4am/Lzz2ZtA35Wuaa7u8w6R6fG1DX2aFuD4rZ9S0M3/gHSkM8Pf6aw8NAPn6Rzk8T5lpr0g7uhPryHe3+PgazrDD+FjhPxO65x/CCCxukSyZeFHgsXUpiOPY31X612A32BtvID/Vd9SkOkGRFnLu23WyOIO0fkI8PsOAr8ZJfS0ud0+3aJ1/fhzrt6kvcg+WsLjHeIswkQQ="

private const val CERT_SHA256 = "FEAB54C4AD8175C6191E852DE4887638C7B3E7121D7DAB1C369BA0D426FE5F02"
private const val NATIVE_KEK = "56E164D0280D23B7DFC33E920548CB3D3113185D3A0B6C6761DEF99E927846C1"

@Suppress("unused")
val signerPatch = patch(
    name = "Signer Identity",
    description = "Forces the app's signer identity to match the original publisher certificate.",
    default = true
) {
    compatibleWith(COMPATIBILITY)

    execute {
        // jO2pqaX.pX6SrQYLl(PackageInfo) -> return the original certificate as a singleton list.
        Fingerprint(
            definingClass = "LjO2pqaX;",
            name = "pX6SrQYLl",
            parameters = listOf("Landroid/content/pm/PackageInfo;"),
            returnType = "Ljava/util/List;"
        ).method.addInstructions(
            0,
            """
                const-string v0, $CERT_BASE64
                const/4 v1, 0x0
                invoke-static {v0, v1}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B
                move-result-object v0
                invoke-static {v0}, Ljava/util/Collections;->singletonList(Ljava/lang/Object;)Ljava/util/List;
                move-result-object v0
                return-object v0
            """.trimIndent()
        )

        // NativeIdentity.xo0eU(Context) -> return the original cert SHA-256.
        Fingerprint(
            definingClass = "Lcom/angkin/keyflow/nativeverify/NativeIdentity;",
            name = "xo0eU",
            parameters = listOf("Landroid/content/Context;"),
            returnType = "Ljava/lang/String;"
        ).method.addInstructions(
            0,
            """
                const-string v0, $CERT_SHA256
                return-object v0
            """.trimIndent()
        )

        // NativeIdentity.KEsFip5xs(Context) -> return the native KEK.
        Fingerprint(
            definingClass = "Lcom/angkin/keyflow/nativeverify/NativeIdentity;",
            name = "KEsFip5xs",
            parameters = listOf("Landroid/content/Context;"),
            returnType = "Ljava/lang/String;"
        ).method.addInstructions(
            0,
            """
                const-string v0, $NATIVE_KEK
                return-object v0
            """.trimIndent()
        )

        // NativeIdentity.pX6SrQYLl() -> return true.
        Fingerprint(
            definingClass = "Lcom/angkin/keyflow/nativeverify/NativeIdentity;",
            name = "pX6SrQYLl",
            parameters = listOf(),
            returnType = "Z"
        ).method.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """.trimIndent()
        )
    }
}
