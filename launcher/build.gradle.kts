import java.net.URI
import java.security.MessageDigest

plugins {
    application
}

description = "Starts Minecraft through ThaiFlowMC: wires the loader, Python runtime, and Minecraft adapter together."

dependencies {
    implementation(project(":api"))
    implementation(project(":loader"))
    implementation(project(":python-runtime"))
    implementation(project(":minecraft-adapter"))

    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")
}

application {
    mainClass.set("dev.thaiflowmc.launcher.Launcher")
    // Silences JDK warnings from running GraalPy on a stock (non-GraalVM) JDK;
    // harmless, but noisy for what's supposed to be a beginner-friendly console.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow")
}

// --- Real Minecraft integration (see docs/ROADMAP.md) -----------------------
//
// Manual/reproducible only: never wired into `build`, `check`, or `test`.
// Downloads a ~50MB official Mojang jar and actually runs a Minecraft
// server, which requires the operator to have already agreed to Mojang's
// EULA in run/eula.txt - ThaiFlowMC does not do that automatically.

val minecraftServerVersion = "26.3"
val minecraftServerSha1 = "33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c"
val minecraftServerUrl =
    "https://piston-data.mojang.com/v1/objects/$minecraftServerSha1/server.jar"

val downloadMinecraftServer = tasks.register("downloadMinecraftServer") {
    description = "Downloads the official Mojang $minecraftServerVersion dedicated server jar into run/ (never committed)."
    val outputFile = rootProject.file("run/server.jar")
    outputs.file(outputFile)
    doLast {
        if (!outputFile.exists()) {
            outputFile.parentFile.mkdirs()
            logger.lifecycle("Downloading Minecraft $minecraftServerVersion server jar from Mojang...")
            URI(minecraftServerUrl).toURL().openStream().use { input ->
                outputFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val digest = MessageDigest.getInstance("SHA-1")
        val actualSha1 = digest.digest(outputFile.readBytes()).joinToString("") { b -> "%02x".format(b) }
        if (actualSha1 != minecraftServerSha1) {
            throw GradleException(
                "run/server.jar sha1 $actualSha1 does not match the expected Mojang $minecraftServerVersion " +
                    "server jar sha1 $minecraftServerSha1 - delete run/server.jar and re-run this task."
            )
        }
    }
}

val minecraftAdapterJar = project(":minecraft-adapter").tasks.named("jar", Jar::class)

tasks.register<JavaExec>("realMinecraftIntegration") {
    description = "Launches the real Minecraft $minecraftServerVersion dedicated server with ThaiFlowMC's " +
        "agent attached. Requires run/eula.txt to already contain eula=true (see docs/ROADMAP.md)."
    group = "thaiflowmc"
    dependsOn(downloadMinecraftServer, minecraftAdapterJar)
    mainClass.set("dev.thaiflowmc.launcher.RealMinecraftLauncher")
    classpath = sourceSets["main"].runtimeClasspath + files(rootProject.file("run/server.jar"))
    workingDir = rootProject.file("run")
    jvmArgs = listOf("-javaagent:${minecraftAdapterJar.get().archiveFile.get().asFile.absolutePath}")
    args = listOf(rootProject.file("mods").absolutePath, "nogui")
    // Forwards stdin so the standard Minecraft "stop" console command works
    // for a clean shutdown (also flushes output, unlike killing the process).
    standardInput = System.`in`
}
