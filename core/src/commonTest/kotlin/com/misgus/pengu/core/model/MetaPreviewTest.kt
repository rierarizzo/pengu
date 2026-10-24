package com.misgus.pengu.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The artwork shape an addon declares for a catalog item. */
class MetaPreviewTest {
    private fun preview(posterShape: String?) = MetaPreview(id = "tt1", type = ContentTypes.MOVIE, name = "X", posterShape = posterShape)

    @Test
    fun aLandscapePosterShapeMarksTheArtworkWide() {
        // Addons spell it lowercase, but the value is third-party data.
        assertTrue(preview("landscape").isLandscape)
        assertTrue(preview("Landscape").isLandscape)
    }

    @Test
    fun everyOtherShapeStaysAPortraitPoster() {
        assertFalse(preview("poster").isLandscape)
        assertFalse(preview("square").isLandscape)
        assertFalse(preview(null).isLandscape)
    }
}
