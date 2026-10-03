package com.vibe.store.infrastructure.media

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.vibe.store.api.ProductImage
import com.vibe.store.api.SelectedImage
import com.vibe.store.api.TeamError
import com.vibe.store.api.TeamFailure
import com.vibe.store.application.team.ProfilePhotoMedia
import com.vibe.store.domain.MediaPolicy
import com.vibe.store.domain.MediaSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Opaque and short-lived. The external URI is never stored in the database. */
private class ProfileSelection(val uri: Uri) : SelectedImage

class AndroidProfilePhotoMedia private constructor(
    private val open: (Uri) -> InputStream?,
) : ProfilePhotoMedia {

    companion object {
        private const val MAX_DATA_URL_LENGTH = 750_000

        fun create(context: Context): AndroidProfilePhotoMedia =
            AndroidProfilePhotoMedia {
                context.contentResolver.openInputStream(it)
            }

        fun selection(uri: Uri): SelectedImage {
            if (uri.scheme != "content") {
                throw TeamFailure(TeamError.INVALID_MEDIA)
            }

            return ProfileSelection(uri)
        }

        internal fun fixture(
            open: (Uri) -> InputStream?,
        ): AndroidProfilePhotoMedia =
            AndroidProfilePhotoMedia(open)
    }

    private fun fail(code: TeamError): Nothing =
        throw TeamFailure(code)

    override suspend fun encode(
        selection: SelectedImage,
    ): String = withContext(Dispatchers.IO) {
        val source = selection as? ProfileSelection
            ?: fail(TeamError.INVALID_MEDIA)

        if (source.uri.scheme != "content") {
            fail(TeamError.INVALID_MEDIA)
        }

        val input = try {
            open(source.uri)
        } catch (_: SecurityException) {
            fail(TeamError.MEDIA_UNAVAILABLE)
        } catch (_: IOException) {
            fail(TeamError.MEDIA_UNAVAILABLE)
        } ?: fail(TeamError.MEDIA_UNAVAILABLE)

        val bytes = try {
            input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                val maximum = MediaPolicy.PROFILE.maximumBytes

                while (true) {
                    val count = stream.read(buffer)

                    if (count == -1) break
                    if (count == 0) continue

                    if (count > maximum - output.size()) {
                        fail(TeamError.MEDIA_TOO_LARGE)
                    }

                    output.write(buffer, 0, count)
                }

                output.toByteArray()
            }
        } catch (_: SecurityException) {
            fail(TeamError.MEDIA_UNAVAILABLE)
        } catch (_: IOException) {
            fail(TeamError.MEDIA_UNAVAILABLE)
        }

        val kind = MediaSignature.detect(bytes)
            ?: fail(TeamError.INVALID_MEDIA)

        val encoded = Base64.encodeToString(
            bytes,
            Base64.NO_WRAP,
        )

        val dataUrl =
            "data:${kind.mimeType};base64,$encoded"

        if (dataUrl.length > MAX_DATA_URL_LENGTH) {
            fail(TeamError.MEDIA_TOO_LARGE)
        }

        dataUrl
    }

    override suspend fun decode(
        dataUrl: String,
    ): ProductImage = withContext(Dispatchers.IO) {
        if (
            dataUrl.isEmpty() ||
            dataUrl.length > MAX_DATA_URL_LENGTH
        ) {
            fail(TeamError.INVALID_MEDIA)
        }

        val separator = dataUrl.indexOf(',')

        if (separator <= 0) {
            fail(TeamError.INVALID_MEDIA)
        }

        val header = dataUrl.substring(0, separator)
        val body = dataUrl.substring(separator + 1)

        if (
            header !in setOf(
                "data:image/jpeg;base64",
                "data:image/png;base64",
                "data:image/webp;base64",
            )
        ) {
            fail(TeamError.INVALID_MEDIA)
        }

        if (
            body.isEmpty() ||
            !Regex("[A-Za-z0-9+/]+={0,2}").matches(body)
        ) {
            fail(TeamError.INVALID_MEDIA)
        }

        val bytes = try {
            Base64.decode(body, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            fail(TeamError.INVALID_MEDIA)
        }

        if (
            bytes.size > MediaPolicy.PROFILE.maximumBytes
        ) {
            fail(TeamError.MEDIA_TOO_LARGE)
        }

        // Reject non-canonical Base64 instead of silently accepting
        // altered or ambiguous representations.
        if (
            Base64.encodeToString(
                bytes,
                Base64.NO_WRAP,
            ) != body
        ) {
            fail(TeamError.INVALID_MEDIA)
        }

        val kind = MediaSignature.detect(bytes)
            ?: fail(TeamError.INVALID_MEDIA)

        if (header != "data:${kind.mimeType};base64") {
            fail(TeamError.INVALID_MEDIA)
        }

        ProductImage(kind.mimeType, bytes)
    }
}
