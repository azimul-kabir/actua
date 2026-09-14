package com.azimulkabir.actua.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Round-trips Actua's `cleanup_def` codec against Actual's `CleanupTemplate[]` shape
 * (`packages/loot-core/src/types/models/cleanup-templates.ts` at commit `2fc69915`).
 */
@RunWith(AndroidJUnit4::class)
class CleanupTargetCodecTest {
    @Test fun globalSourceRoundTrips() {
        val targets = listOf(CleanupTarget(CleanupTarget.Role.SOURCE, groupId = null))
        val encoded = requireNotNull(CleanupTarget.encode(targets))
        assertEquals("""[{"role":"source","groupId":null}]""", encoded)
        assertEquals(targets, CleanupTarget.decode(encoded).targets)
        assertEquals(false, CleanupTarget.decode(encoded).invalid)
    }

    @Test fun groupScopedSinkWithWeightRoundTrips() {
        val targets = listOf(CleanupTarget(CleanupTarget.Role.SINK, groupId = "vacation", weight = 3))
        val encoded = requireNotNull(CleanupTarget.encode(targets))
        val decoded = CleanupTarget.decode(encoded)
        assertEquals(targets, decoded.targets)
        assertEquals(false, decoded.invalid)
    }

    @Test fun overspendRequiresAGroupId() {
        // Actual's overspend directive is always attached to a named cleanup group; a bare
        // global overspend row has no meaning and must be disclosed as invalid, never dropped
        // silently or approximated as a group-less overspend.
        val decoded = CleanupTarget.decode("""[{"role":"overspend","groupId":null}]""")
        assertEquals(true, decoded.invalid)
        assertEquals(emptyList<CleanupTarget>(), decoded.targets)
    }

    @Test fun unparseableJsonIsDisclosedAsInvalidRatherThanDropped() {
        val decoded = CleanupTarget.decode("not json")
        assertEquals(true, decoded.invalid)
    }

    @Test fun nullOrBlankDefinitionDecodesToNoTargetsWithoutBeingInvalid() {
        assertEquals(false, CleanupTarget.decode(null).invalid)
        assertEquals(emptyList<CleanupTarget>(), CleanupTarget.decode(null).targets)
        assertEquals(emptyList<CleanupTarget>(), CleanupTarget.decode("[]").targets)
        assertEquals(false, CleanupTarget.decode("[]").invalid)
    }

    @Test fun emptyTargetListEncodesToNull() {
        assertNull(CleanupTarget.encode(emptyList()))
    }

    @Test fun nonPositiveSinkWeightIsDisclosedAsInvalid() {
        val decoded = CleanupTarget.decode("""[{"role":"sink","groupId":"g1","weight":0}]""")
        assertEquals(true, decoded.invalid)
    }

    @Test fun unknownRoleIsDisclosedAsInvalid() {
        val decoded = CleanupTarget.decode("""[{"role":"mystery","groupId":null}]""")
        assertEquals(true, decoded.invalid)
    }
}
