package io.github.yanziki.enterpriseai.knowledge.storage;

import java.io.InputStream;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3Client;
    private final StorageProperties properties;

    public S3ObjectStorage(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public void put(String objectKey, Path source, long contentLength, String contentType) {
        PutObjectRequest request =
                PutObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .contentLength(contentLength)
                        .contentType(contentType)
                        .build();
        try {
            s3Client.putObject(request, RequestBody.fromFile(source));
        } catch (SdkException exception) {
            throw new ObjectStorageException("Could not store document bytes", exception);
        }
    }

    @Override
    public StoredObject get(String objectKey) {
        try {
            ResponseInputStream<GetObjectResponse> response =
                    s3Client.getObject(
                            GetObjectRequest.builder()
                                    .bucket(properties.bucket())
                                    .key(objectKey)
                                    .build());
            InputStream content = response;
            return new StoredObject(
                    content,
                    response.response().contentLength(),
                    response.response().contentType());
        } catch (SdkException exception) {
            throw new ObjectStorageException("Could not read document bytes", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .build());
        } catch (SdkException exception) {
            throw new ObjectStorageException("Could not delete document bytes", exception);
        }
    }
}
