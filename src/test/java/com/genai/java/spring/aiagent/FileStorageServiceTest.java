package com.genai.java.spring.aiagent;

import com.genai.java.spring.aiagent.config.data.AIAgentConfigData;
import com.genai.java.spring.aiagent.exception.SecurityReviewAgentException;
import com.genai.java.spring.aiagent.service.impl.FileStorageServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileStorageServiceTest {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00};

    @Test
    @DisplayName("@spec:AC-010 Payload e upload inválidos são rejeitados sem persistir arquivos")
    void rejectsEmptyOversizedAndMismatchedUploadsWithoutPersistingFiles(@TempDir Path uploadDirectory) throws IOException {
        FileStorageServiceImpl storage = storageAt(uploadDirectory);

        assertThrows(SecurityReviewAgentException.class,
                () -> storage.save(new MockMultipartFile("diagram", "empty.png", "image/png", new byte[0])));
        assertDirectoryIsEmpty(uploadDirectory);

        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        oversized[0] = (byte) 0x89;
        assertThrows(SecurityReviewAgentException.class,
                () -> storage.save(new MockMultipartFile("diagram", "large.png", "image/png", oversized)));
        assertDirectoryIsEmpty(uploadDirectory);

        assertThrows(SecurityReviewAgentException.class,
                () -> storage.save(new MockMultipartFile("diagram", "diagram.png", "image/png", "%PDF-1.7".getBytes())));
        assertDirectoryIsEmpty(uploadDirectory);
    }

    @Test
    @DisplayName("@spec:AC-010 XML externo e extensão incompatível são rejeitados sem persistir arquivos")
    void rejectsUnsafeXmlAndMismatchedContentTypeWithoutPersistingFiles(@TempDir Path uploadDirectory) throws IOException {
        FileStorageServiceImpl storage = storageAt(uploadDirectory);
        byte[] unsafeSvg = "<!DOCTYPE svg [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><svg>&xxe;</svg>".getBytes();

        assertThrows(SecurityReviewAgentException.class,
                () -> storage.save(new MockMultipartFile("diagram", "diagram.svg", "image/svg+xml", unsafeSvg)));
        assertDirectoryIsEmpty(uploadDirectory);

        assertThrows(SecurityReviewAgentException.class,
                () -> storage.save(new MockMultipartFile("diagram", "diagram.png", "image/jpeg", PNG)));
        assertDirectoryIsEmpty(uploadDirectory);
    }

    private FileStorageServiceImpl storageAt(Path uploadDirectory) throws IOException {
        AIAgentConfigData configuration = new AIAgentConfigData();
        configuration.setUploadDir(uploadDirectory.toString());
        return new FileStorageServiceImpl(configuration);
    }

    private void assertDirectoryIsEmpty(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }
}
