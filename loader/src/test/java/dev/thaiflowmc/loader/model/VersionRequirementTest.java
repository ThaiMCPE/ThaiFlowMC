package dev.thaiflowmc.loader.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionRequirementTest {

    @Test
    void gteMatchesEqualAndGreater() {
        VersionRequirement req = VersionRequirement.parse(">=0.1.0");
        assertTrue(req.matches(Version.parse("0.1.0")));
        assertTrue(req.matches(Version.parse("0.2.0")));
        assertFalse(req.matches(Version.parse("0.0.9")));
    }

    @Test
    void exactRequiresEquality() {
        VersionRequirement req = VersionRequirement.parse("1.2.0");
        assertTrue(req.matches(Version.parse("1.2.0")));
        assertFalse(req.matches(Version.parse("1.2.1")));
    }

    @Test
    void supportsLtLteGt() {
        assertTrue(VersionRequirement.parse("<2.0.0").matches(Version.parse("1.9.9")));
        assertTrue(VersionRequirement.parse("<=2.0.0").matches(Version.parse("2.0.0")));
        assertTrue(VersionRequirement.parse(">2.0.0").matches(Version.parse("2.0.1")));
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> VersionRequirement.parse("~>1.0.0"));
        assertThrows(IllegalArgumentException.class, () -> VersionRequirement.parse(">=not-a-version"));
    }
}
