package org.session.libsession.messaging.contacts

import org.session.libsession.utilities.recipients.Recipient

class Contact(val sessionID: String) {
    // The URL from which to fetch the contact's profile picture.
    var profilePictureURL: String? = null
    // The file name of the contact's profile picture on local storage.
    var profilePictureFileName: String? = null
    // The key with which the profile picture is encrypted.
    var profilePictureEncryptionKey: ByteArray? = null
    // The ID of the thread associated with this contact.
    var threadID: Long? = null
    // This flag is used to determine whether we should auto-download files sent by this contact.
    var isTrusted = false

    // region: Name
    // The name of the contact. Use this whenever you need the "real", underlying name of a user (e.g. when sending a message).
    var name: String? = null
    // The contact's nickname, if the user set one.
    var nickname: String? = null
    // The name to display in the UI. For local use only.
    fun displayName(context: ContactContext): String? {
        this.nickname?.let { return it }
        return when {
            context == ContactContext.REGULAR -> this.name
            context == ContactContext.OPEN_GROUP -> {
                // In open groups, where it's more likely that multiple users have the same name,
                // we display a bit of the Session ID after a user's display name for added context.
                this.name?.let {
                    return "${this.name}${this.sessionID.takeLast(8)}"
                }
                return null
            }
            else -> throw Exception("Unknown contact context!")
        }
    }
    //end region

    enum class ContactContext {
        REGULAR, OPEN_GROUP
    }

    fun isValid(): Boolean {
        if (profilePictureURL != null) { return profilePictureEncryptionKey != null }
        if (profilePictureEncryptionKey != null) { return profilePictureURL != null}
        return true
    }

    override fun equals(other: Any?): Boolean {
        return if (other is Contact) {
            other.sessionID == this.sessionID
        } else {
            false
        }
    }

    override fun hashCode(): Int {
        return sessionID.hashCode()
    }

    override fun toString(): String {
        return nickname ?: name ?: sessionID
    }

    companion object {
        fun contextForRecipient(recipient: Recipient): ContactContext {
            return if (recipient.isOpenGroupRecipient) { ContactContext.OPEN_GROUP }
            else { ContactContext.REGULAR }
        }
    }
}