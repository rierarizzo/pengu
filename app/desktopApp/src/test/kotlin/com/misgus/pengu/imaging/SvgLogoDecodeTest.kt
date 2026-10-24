package com.misgus.pengu.imaging

import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.request.transformations
import coil3.size.Size
import coil3.toBitmap
import com.misgus.pengu.ui.components.TrimTransparentTransformation
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The desktop loader must decode an SVG title logo.
 *
 * Providers serve many logos as SVG (a newer series can have one and nothing else),
 * and the app's own raster decoder claims every source, so an SVG that reached
 * it failed to paint at all. The logo request also has to ask for a real
 * resolution: an SVG has a viewBox, not pixels, so decoding it at
 * `Size.ORIGINAL` rasterises a 100-unit-wide wordmark at 100px.
 */
class SvgLogoDecodeTest {
    /** A wordmark-only SVG: 100x20 units, filled edge to edge like a real logo. */
    private val svg =
        """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 20">
          <rect width="100" height="20" fill="#000000"/>
        </svg>
        """.trimIndent().encodeToByteArray()

    @Test
    fun decodesTheLogoAtTheRequestedResolution() =
        runBlocking {
            configureImageLoader()
            val loader = SingletonImageLoader.get(PlatformContext.INSTANCE)

            val result =
                loader.execute(
                    ImageRequest
                        .Builder(PlatformContext.INSTANCE)
                        .data(svg)
                        .size(Size(2_048, 2_048))
                        .maxBitmapSize(Size(2_048, 2_048))
                        .transformations(TrimTransparentTransformation())
                        .build(),
                )

            val bitmap = requireNotNull(result.image?.toBitmap())
            // 100 units at the 2048 box, not the 100px the viewBox alone would give.
            assertTrue(bitmap.width > 1_000, "decoded ${bitmap.width}x${bitmap.height}")
        }
}
