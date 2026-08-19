package com.genai.java.spring.multimodality;

import com.genai.java.spring.multimodality.texttoimage.GeneratedImageStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GeneratedImageStorageTest {

    @Test
    @DisplayName("@spec:AC-013 Imagens concorrentes recebem arquivos distintos sem sobrescrita")
    void storesConcurrentGeneratedImagesUnderDistinctNames(@TempDir Path storageDirectory) throws Exception {
        GeneratedImageStorage storage = new GeneratedImageStorage(storageDirectory);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> first = () -> storage.save("first image".getBytes(StandardCharsets.UTF_8));
            Callable<String> second = () -> storage.save("second image".getBytes(StandardCharsets.UTF_8));
            List<Future<String>> savedImages = executor.invokeAll(List.of(first, second));

            String firstName = savedImages.get(0).get();
            String secondName = savedImages.get(1).get();

            assertNotEquals(firstName, secondName);
            assertEquals("first image", Files.readString(storage.resolve(firstName)));
            assertEquals("second image", Files.readString(storage.resolve(secondName)));
        } finally {
            executor.shutdownNow();
        }
    }
}
