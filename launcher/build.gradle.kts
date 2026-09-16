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
