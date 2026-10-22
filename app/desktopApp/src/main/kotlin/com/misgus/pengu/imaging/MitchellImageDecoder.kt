package com.misgus.pengu.imaging

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.svg.SvgDecoder
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode

/**
 * Replaces Coil's desktop [coil3.decode.SkiaImageDecoder].
 *
 * Coil 3.6.3 downsamples with [SamplingMode.DEFAULT], which Skiko defines as
 * nearest-neighbour with no mipmap. Every image painted below its source
 * resolution — posters, backdrops, cast photos, title logos — is therefore
 * aliased ("jagged"). This decoder keeps Coil's size math but samples with
 * [SamplingMode.MITCHELL], so a large image is reduced once, at decode time,
 * with a proper reconstruction filter instead of being point-sampled.
 */
class MitchellImageDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult? {
        val bytes = source.source().use { it.readByteArray() }
        return decodeBitmap(options, bytes)
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder = MitchellImageDecoder(result.source, options)
    }
}

/**
 * Installs the desktop [ImageLoader] that downsamples with a cubic filter.
 *
 * Must run before the composition renders its first image.
 */
fun configureImageLoader() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader
            .Builder(context)
            .components {
                // SVG title logos come first: the raster decoder below claims every
                // source, and Skia cannot rasterise an SVG from its bytes, so an SVG
                // that reached it would fail to paint at all. This one answers only
                // for SVG and lets everything else fall through.
                add(SvgDecoder.Factory())
                add(MitchellImageDecoder.Factory())
            }.build()
    }
}

@OptIn(ExperimentalCoilApi::class)
private fun decodeBitmap(
    options: Options,
    bytes: ByteArray,
): DecodeResult {
    val image = Image.makeFromEncoded(bytes)
    val bitmap: Bitmap
    val isSampled: Boolean
    try {
        bitmap = image.scaledTo(options)
        bitmap.setImmutable()
        isSampled = bitmap.width < image.width || bitmap.height < image.height
    } finally {
        image.close()
    }
    return DecodeResult(image = bitmap.asImage(), isSampled = isSampled)
}

/** Mirrors Coil's own decoder, but reduces with [SamplingMode.MITCHELL]. */
@OptIn(ExperimentalCoilApi::class)
private fun Image.scaledTo(options: Options): Bitmap {
    val dst =
        DecodeUtils.computeDstSize(
            srcWidth = width,
            srcHeight = height,
            targetSize = options.size,
            scale = options.scale,
            maxSize = options.maxBitmapSize,
        )
    var multiplier =
        DecodeUtils.computeSizeMultiplier(
            srcWidth = width,
            srcHeight = height,
            dstWidth = dst.first,
            dstHeight = dst.second,
            scale = options.scale,
            maxSize = options.maxBitmapSize,
        )
    // Match Coil: never upscale unless an exact size was requested.
    if (options.precision == Precision.INEXACT) {
        multiplier = multiplier.coerceAtMost(1.0)
    }
    val outWidth = (multiplier * width).toInt().coerceAtLeast(1)
    val outHeight = (multiplier * height).toInt().coerceAtLeast(1)

    val bitmap = Bitmap()
    bitmap.allocN32Pixels(outWidth, outHeight)
    val canvas = Canvas(bitmap)
    try {
        canvas.drawImageRect(
            image = this,
            srcLeft = 0f,
            srcTop = 0f,
            srcRight = width.toFloat(),
            srcBottom = height.toFloat(),
            dstLeft = 0f,
            dstTop = 0f,
            dstRight = outWidth.toFloat(),
            dstBottom = outHeight.toFloat(),
            samplingMode = SamplingMode.MITCHELL,
            paint = null,
            strict = false,
        )
    } finally {
        canvas.close()
    }
    return bitmap
}
