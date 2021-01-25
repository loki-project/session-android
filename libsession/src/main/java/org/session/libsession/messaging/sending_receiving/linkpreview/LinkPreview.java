package org.session.libsession.messaging.sending_receiving.linkpreview;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.session.libsession.messaging.sending_receiving.attachments.Attachment;
import org.session.libsession.messaging.sending_receiving.attachments.AttachmentId;
import org.session.libsession.messaging.sending_receiving.attachments.DatabaseAttachment;
import org.session.libsession.utilities.JsonUtils;
import org.session.libsignal.libsignal.util.guava.Optional;

import java.io.IOException;

public class LinkPreview {

  @JsonProperty
  private final String       url;

  @JsonProperty
  private final String       title;

  @JsonProperty
  private final AttachmentId attachmentId;

  @JsonIgnore
  public Optional<Attachment> thumbnail;

  public LinkPreview(@NonNull String url, @NonNull String title, @NonNull DatabaseAttachment thumbnail) {
    this.url          = url;
    this.title        = title;
    this.thumbnail    = Optional.of(thumbnail);
    this.attachmentId = thumbnail.getAttachmentId();
  }

  public LinkPreview(@NonNull String url, @NonNull String title, @NonNull Optional<Attachment> thumbnail) {
    this.url          = url;
    this.title        = title;
    this.thumbnail    = thumbnail;
    this.attachmentId = null;
  }

  public LinkPreview(@JsonProperty("url")          @NonNull  String url,
                     @JsonProperty("title")        @NonNull  String title,
                     @JsonProperty("attachmentId") @Nullable AttachmentId attachmentId)
  {
    this.url          = url;
    this.title        = title;
    this.attachmentId = attachmentId;
    this.thumbnail    = Optional.absent();
  }

  public String getUrl() {
    return url;
  }

  public String getTitle() {
    return title;
  }

  public Optional<Attachment> getThumbnail() {
    return thumbnail;
  }

  public @Nullable AttachmentId getAttachmentId() {
    return attachmentId;
  }

  public String serialize() throws IOException {
    return JsonUtils.toJson(this);
  }

  public static LinkPreview deserialize(@NonNull String serialized) throws IOException {
    return JsonUtils.fromJson(serialized, LinkPreview.class);
  }
}