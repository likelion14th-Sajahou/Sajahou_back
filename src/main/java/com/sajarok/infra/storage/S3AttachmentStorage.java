package com.sajarok.infra.storage;

import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@Component
public class S3AttachmentStorage {

  private final S3Client s3Client;
  private final String bucket;

  public S3AttachmentStorage(
      S3Client s3Client, @Value("${attachment.storage.bucket:}") String bucket) {
    this.s3Client = s3Client;
    this.bucket = bucket;
  }

  public void upload(String key, InputStream input, long sizeBytes, String contentType) {
    s3Client.putObject(
        request -> request.bucket(bucket).key(key).contentType(contentType),
        RequestBody.fromInputStream(input, sizeBytes));
  }

  public void delete(String key) {
    s3Client.deleteObject(request -> request.bucket(bucket).key(key));
  }
}
