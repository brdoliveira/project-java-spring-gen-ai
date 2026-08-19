package com.genai.java.spring.multimodality.texttoimage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Component
public class GeneratedImageStorage {

    private final Path root;

    public GeneratedImageStorage(@Value("${app.marketing-assets.storage-dir:uploads/generated-images}") String storageDirectory) throws IOException {
        this(Path.of(storageDirectory));
    }

    public GeneratedImageStorage(Path storageDirectory) throws IOException {
        root = storageDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String save(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("Generated image must not be empty");
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            String fileName = UUID.randomUUID() + ".png";
            Path target = root.resolve(fileName).normalize();
            if (!target.startsWith(root)) {
                throw new IllegalStateException("Generated image path escaped its storage directory");
            }
            try {
                Files.write(target, imageBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return fileName;
            } catch (FileAlreadyExistsException ignored) {
                // UUID collisions are retried without ever replacing an existing asset.
            } catch (IOException exception) {
                throw new IllegalStateException("Could not store generated image", exception);
            }
        }
        throw new IllegalStateException("Could not allocate a unique generated image name");
    }

    public Path resolve(String fileName) {
        Path target = root.resolve(fileName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid generated image name");
        }
        return target;
    }
}
