plugins {
    id("dev.kikugie.stonecutter")
}
stonecutter active "1.21.1-neoforge" /* [SC] DO NOT EDIT */

// Offline Pheno pack validation (no Minecraft launch). Runs the structural linter over
// TownsteadPacks; pass -PphenoManifest=<types-manifest.json from /pheno dump> for type checks,
// or -PphenoPacks=<comma,separated,dirs> to override the targets.
tasks.register<Exec>("validatePacks") {
    group = "verification"
    description = "Offline-validate TownsteadPacks gene JSON without launching Minecraft."
    val defaultPacks = listOf(
        "$rootDir/../TownsteadPacks/origins/testing",
        "$rootDir/../TownsteadPacks/origins/classic-fantasy"
    )
    val packs = (findProperty("phenoPacks") as String?)?.split(",") ?: defaultPacks
    val manifest = findProperty("phenoManifest") as String?
    val cmd = mutableListOf("python", "$rootDir/tools/pheno-validate.py")
    cmd.addAll(packs)
    if (manifest != null) {
        cmd.add("--manifest")
        cmd.add(manifest)
    }
    commandLine(cmd)
}
