package org.jellyfin.androidtv.util

import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Size

/**
 * Extension function to apply quality optimizations to an ImageRequest
 */
fun ImageRequest.Builder.applyQualityOptimizations(
    quality: Int = 60,
    precision: Precision = Precision.INEXACT
): ImageRequest.Builder = apply {
    // Set precision (controls how the image is resized)
    precision(precision)

    // For Coil 3.x, we can only control quality through size and precision
    // The actual quality reduction will be handled by the server if it supports it

    // You can also try reducing the size to improve performance
    // size(Size.ORIGINAL) // Keep original size for now
}
