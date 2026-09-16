description = "Isolates ThaiFlowMC from a specific Minecraft version. " +
        "Real Minecraft wiring is in progress; see docs/ROADMAP.md."

dependencies {
    api(project(":api"))

    // Used by the real (non-Fabric/Forge/Quilt) hook mechanism in `mc`:
    // ServerStartHookTransformer patches Minecraft's own bytecode via a
    // java.lang.instrument agent, without any modding-loader framework.
    implementation("org.ow2.asm:asm:9.10.1")
    testImplementation("org.ow2.asm:asm-util:9.10.1")
}

tasks.jar {
    manifest {
        attributes(
            "Premain-Class" to "dev.thaiflowmc.adapter.mc.ThaiFlowAgent",
            "Agent-Class" to "dev.thaiflowmc.adapter.mc.ThaiFlowAgent",
            "Can-Retransform-Classes" to "true",
        )
    }
}
