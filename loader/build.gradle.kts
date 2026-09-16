description = "Discovers mods, parses mod.toml, resolves dependencies and drives mod lifecycle."

dependencies {
    api(project(":api"))
    implementation("org.tomlj:tomlj:1.1.1")
}
