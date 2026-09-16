package dev.thaiflowmc.loader.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void parsesFullVersion() {
        Version v = Version.parse("1.2.3");
        assertEquals(1, v.major());
        assertEquals(2, v.minor());
        assertEquals(3, v.patch());
        assertEquals("1.2.3", v.toString());
    }

    @Test
    void defaultsMissingComponentsToZero() {
        assertEquals(Version.parse("1.0.0"), Version.parse("1"));
        assertEquals(Version.parse("1.2.0"), Version.parse("1.2"));
    }

    @Test
    void ordersByMajorThenMinorThenPatch() {
        assertTrue(Version.parse("2.0.0").compareTo(Version.parse("1.9.9")) > 0);
        assertTrue(Version.parse("1.2.0").compareTo(Version.parse("1.1.9")) > 0);
        assertTrue(Version.parse("1.1.2").compareTo(Version.parse("1.1.1")) > 0);
    }

    @Test
    void rejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse("not-a-version"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1.2.3.4"));
        assertThrows(IllegalArgumentException.class, () -> Version.parse(""));
    }
}
