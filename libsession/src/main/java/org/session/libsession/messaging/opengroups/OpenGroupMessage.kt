package org.session.libsession.messaging.opengroups

import org.session.libsession.messaging.Configuration
import org.session.libsession.messaging.messages.visible.VisibleMessage
import org.session.libsignal.libsignal.logging.Log
import org.session.libsignal.service.internal.util.Hex
import org.session.libsignal.service.loki.utilities.removing05PrefixIfNeeded
import org.whispersystems.curve25519.Curve25519

public data class OpenGroupMessage(
        public val serverID: Long?,
        public val senderPublicKey: String,
        public val displayName: String,
        public val body: String,
        public val timestamp: Long,
        public val type: String,
        public val quote: Quote?,
        public val attachments: List<Attachment>,
        public val profilePicture: ProfilePicture?,
        public val signature: Signature?,
        public val serverTimestamp: Long,
) {

    // region Settings
    companion object {
        fun from(message: VisibleMessage, server: String): OpenGroupMessage? {
            val storage = Configuration.shared.storage
            val userPublicKey = storage.getUserPublicKey() ?: return null
            // Validation
            if (!message.isValid()) { return null } // Should be valid at this point
            // Quote
            val quote: Quote? = {
                val quote = message.quote
                if (quote != null && quote.isValid()) {
                    val quotedMessageServerID = storage.getQuoteServerID(quote.id, quote.publicKey)
                    Quote(quote.timestamp!!, quote.publicKey!!, quote.text!!, quotedMessageServerID)
                } else {
                    null
                }
            }()
            // Message
            val displayname = storage.getUserDisplayName() ?: "Anonymous"
            val body = message.text ?: message.sentTimestamp.toString() // The back-end doesn't accept messages without a body so we use this as a workaround
            val result = OpenGroupMessage(null, userPublicKey, displayname, body, message.sentTimestamp!!, OpenGroupAPI.openGroupMessageType, quote, mutableListOf(), null, null, 0)
            // Link preview
            val linkPreview = message.linkPreview
            linkPreview?.let {
                if (!linkPreview.isValid()) { return@let }
                val attachment = linkPreview.getImage() ?: return@let
                val openGroupLinkPreview = Attachment(
                        Attachment.Kind.LinkPreview,
                        server,
                        attachment.getId(),
                        attachment.getContentType(),
                        attachment.getSize(),
                        attachment.getFileName(),
                        attachment.getFlags(),
                        attachment.getWidth(),
                        attachment.getHeight(),
                        attachment.getCaption(),
                        attachment.getUrl(),
                        linkPreview.url,
                        linkPreview.title)
                result.attachments.add(openGroupLinkPreview)
            }
            // Attachments
            val attachments = message.getAttachemnts().forEach {
                val attachement = Attachment(
                        Attachment.Kind.Attachment,
                        server,
                        it.getId(),
                        it.getContentType(),
                        it.getSize(),
                        it.getFileName(),
                        it.getFlags(),
                        it.getWidth(),
                        it.getHeight(),
                        it.getCaption(),
                        it.getUrl(),
                        linkPreview.getUrl(),
                        linkPreview.getTitle())
                result.attachments.add(attachement)
            }
            // Return
            return result
        }

        private val curve = Curve25519.getInstance(Curve25519.BEST)
        private val signatureVersion: Long = 1
        private val attachmentType = "net.app.core.oembed"
    }
    // endregion

    // region Types
    public data class ProfilePicture(
            public val profileKey: ByteArray,
            public val url: String,
    )

    public data class Quote(
            public val quotedMessageTimestamp: Long,
            public val quoteePublicKey: String,
            public val quotedMessageBody: String,
            public val quotedMessageServerID: Long? = null,
    )

    public data class Signature(
            public val data: ByteArray,
            public val version: Long,
    )

    public data class Attachment(
            public val kind: Kind,
            public val server: String,
            public val serverID: Long,
            public val contentType: String,
            public val size: Int,
            public val fileName: String,
            public val flags: Int,
            public val width: Int,
            public val height: Int,
            public val caption: String?,
            public val url: String,
            /**
            Guaranteed to be non-`nil` if `kind` is `LinkPreview`.
             */
            public val linkPreviewURL: String?,
            /**
            Guaranteed to be non-`nil` if `kind` is `LinkPreview`.
             */
            public val linkPreviewTitle: String?,
    ) {
        public val dotNetAPIType = when {
            contentType.startsWith("image") -> "photo"
            contentType.startsWith("video") -> "video"
            contentType.startsWith("audio") -> "audio"
            else -> "other"
        }

        public enum class Kind(val rawValue: String) {
            Attachment("attachment"), LinkPreview("preview")
        }
    }
    // endregion

    // region Initialization
    constructor(hexEncodedPublicKey: String, displayName: String, body: String, timestamp: Long, type: String, quote: Quote?, attachments: List<Attachment>)
        : this(null, hexEncodedPublicKey, displayName, body, timestamp, type, quote, attachments, null, null, 0)
    // endregion

    // region Crypto
    internal fun sign(privateKey: ByteArray): OpenGroupMessage? {
        val data = getValidationData(signatureVersion)
        if (data == null) {
            Log.d("Loki", "Failed to sign public chat message.")
            return null
        }
        try {
            val signatureData = curve.calculateSignature(privateKey, data)
            val signature = Signature(signatureData, signatureVersion)
            return copy(signature = signature)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to sign public chat message due to error: ${e.message}.")
            return null
        }
    }

    internal fun hasValidSignature(): Boolean {
        if (signature == null) { return false }
        val data = getValidationData(signature.version) ?: return false
        val publicKey = Hex.fromStringCondensed(senderPublicKey.removing05PrefixIfNeeded())
        try {
            return curve.verifySignature(publicKey, data, signature.data)
        } catch (e: Exception) {
            Log.d("Loki", "Failed to verify public chat message due to error: ${e.message}.")
            return false
        }
    }
    // endregion

    // region Parsing
    internal fun toJSON(): Map<String, Any> {
        val value = mutableMapOf<String, Any>("timestamp" to timestamp)
        if (quote != null) {
            value["quote"] = mapOf("id" to quote.quotedMessageTimestamp, "author" to quote.quoteePublicKey, "text" to quote.quotedMessageBody)
        }
        if (signature != null) {
            value["sig"] = Hex.toStringCondensed(signature.data)
            value["sigver"] = signature.version
        }
        val annotation = mapOf("type" to type, "value" to value)
        val annotations = mutableListOf(annotation)
        attachments.forEach { attachment ->
            val attachmentValue = mutableMapOf(
                    // Fields required by the .NET API
                    "version" to 1,
                    "type" to attachment.dotNetAPIType,
                    // Custom fields
                    "lokiType" to attachment.kind.rawValue,
                    "server" to attachment.server,
                    "id" to attachment.serverID,
                    "contentType" to attachment.contentType,
                    "size" to attachment.size,
                    "fileName" to attachment.fileName,
                    "flags" to attachment.flags,
                    "width" to attachment.width,
                    "height" to attachment.height,
                    "url" to attachment.url
            )
            if (attachment.caption != null) { attachmentValue["caption"] = attachment.caption }
            if (attachment.linkPreviewURL != null) { attachmentValue["linkPreviewUrl"] = attachment.linkPreviewURL }
            if (attachment.linkPreviewTitle != null) { attachmentValue["linkPreviewTitle"] = attachment.linkPreviewTitle }
            val attachmentAnnotation = mapOf("type" to attachmentType, "value" to attachmentValue)
            annotations.add(attachmentAnnotation)
        }
        val result = mutableMapOf("text" to body, "annotations" to annotations)
        if (quote?.quotedMessageServerID != null) {
            result["reply_to"] = quote.quotedMessageServerID
        }
        return result
    }
    // endregion

    // region Convenience
    private fun getValidationData(signatureVersion: Long): ByteArray? {
        var string = "${body.trim()}$timestamp"
        if (quote != null) {
            string += "${quote.quotedMessageTimestamp}${quote.quoteePublicKey}${quote.quotedMessageBody.trim()}"
            if (quote.quotedMessageServerID != null) {
                string += "${quote.quotedMessageServerID}"
            }
        }
        string += attachments.sortedBy { it.serverID }.map { it.serverID }.joinToString("")
        string += "$signatureVersion"
        try {
            return string.toByteArray(Charsets.UTF_8)
        } catch (exception: Exception) {
            return null
        }
    }
    // endregion
}