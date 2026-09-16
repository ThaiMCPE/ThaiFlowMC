description = "Embeds GraalPy, loads main.py, and bridges the ThaiFlowMC API into Python."

val graalpyVersion = property("graalpyVersion") as String

dependencies {
    api(project(":api"))

    implementation("org.graalvm.polyglot:polyglot:$graalpyVersion")
    runtimeOnly("org.graalvm.polyglot:python-community:$graalpyVersion")
}
