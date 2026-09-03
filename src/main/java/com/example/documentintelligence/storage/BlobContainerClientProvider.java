package com.example.documentintelligence.storage;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.example.documentintelligence.config.AzureStorageProperties;
import org.springframework.stereotype.Component;

@Component
public class BlobContainerClientProvider {
    private final AzureStorageProperties properties;
    private volatile BlobContainerClient client;

    public BlobContainerClientProvider(AzureStorageProperties properties) {
        this.properties = properties;
    }

    public BlobContainerClient get() {
        BlobContainerClient current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = buildClient();
            }
            return client;
        }
    }

    private BlobContainerClient buildClient() {
        BlobServiceClientBuilder builder = new BlobServiceClientBuilder();
        if (hasText(properties.connectionString())) {
            builder.connectionString(properties.connectionString());
        } else {
            if (!hasText(properties.endpoint())) {
                throw new IllegalStateException(
                        "Set AZURE_STORAGE_ENDPOINT, or AZURE_STORAGE_CONNECTION_STRING for local development.");
            }
            builder.endpoint(properties.endpoint())
                    .credential(new DefaultAzureCredentialBuilder().build());
        }
        return builder.buildClient().getBlobContainerClient(properties.container());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
