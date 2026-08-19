package com.genai.java.spring.aiagent.service.impl;

import com.genai.java.spring.aiagent.config.data.AIAgentConfigData;
import com.genai.java.spring.aiagent.exception.SecurityReviewAgentException;
import com.genai.java.spring.aiagent.service.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    static final long MAX_UPLOAD_BYTES = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "webp", "gif", "svg", "pdf", "drawio", "puml");
    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.ofEntries(
            Map.entry("image/png", "png"),
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/gif", "gif"),
            Map.entry("image/svg+xml", "svg"),
            Map.entry("application/pdf", "pdf"),
            Map.entry("application/xml", "drawio"),
            Map.entry("text/xml", "drawio"),
            Map.entry("text/plain", "puml")
    );

    private final Path root;

    public FileStorageServiceImpl(AIAgentConfigData aiAgentConfigData) throws IOException {
        root = Path.of(aiAgentConfigData.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String save(MultipartFile file) throws SecurityReviewAgentException {
        byte[] content = readBounded(file);
        String extension = inferExtension(file, content);

        if (extension == null || !ALLOWED_EXT.contains(extension)) {
            throw new SecurityReviewAgentException("Unsupported upload type");
        }
        if (!hasMatchingSignature(extension, content) || !hasCompatibleContentType(file, extension)) {
            throw new SecurityReviewAgentException("Upload content does not match its declared type");
        }

        return saveWithUniqueName(extension, content);
    }

    @Override
    public Path resolve(String fileName) {
        Path resolved = root.resolve(fileName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid stored file name");
        }
        return resolved;
    }

    private byte[] readBounded(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_UPLOAD_BYTES) {
            throw new SecurityReviewAgentException(file != null && file.getSize() > MAX_UPLOAD_BYTES
                    ? "Upload exceeds the maximum allowed size"
                    : "Empty upload");
        }

        try (InputStream input = file.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > MAX_UPLOAD_BYTES) {
                    throw new SecurityReviewAgentException("Upload exceeds the maximum allowed size");
                }
                output.write(buffer, 0, read);
            }
            byte[] content = output.toByteArray();
            if (content.length == 0) {
                throw new SecurityReviewAgentException("Empty upload");
            }
            return content;
        } catch (IOException exception) {
            throw new SecurityReviewAgentException("Could not read upload", exception);
        }
    }

    private String inferExtension(MultipartFile file, byte[] content) {
        String extension = extensionFromFileName(file.getOriginalFilename());
        if (extension != null) {
            return extension;
        }

        String detected = detectedBinaryExtension(content);
        if (detected != null) {
            return detected;
        }

        String contentType = file.getContentType();
        return contentType == null ? null : CONTENT_TYPE_EXTENSIONS.get(contentType.toLowerCase(Locale.ROOT));
    }

    private String extensionFromFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return null;
        }
        String filename = Path.of(originalFilename).getFileName().toString();
        int separator = filename.lastIndexOf('.');
        if (separator < 1 || separator == filename.length() - 1) {
            return null;
        }
        String extension = filename.substring(separator + 1).toLowerCase(Locale.ROOT);
        return "jpeg".equals(extension) ? "jpg" : extension;
    }

    private boolean hasCompatibleContentType(MultipartFile file, String extension) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank() || "application/octet-stream".equalsIgnoreCase(contentType)) {
            return true;
        }
        String expectedExtension = CONTENT_TYPE_EXTENSIONS.get(contentType.toLowerCase(Locale.ROOT));
        return extension.equals(expectedExtension);
    }

    private boolean hasMatchingSignature(String extension, byte[] content) {
        return switch (extension) {
            case "png" -> startsWith(content, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "jpg" -> startsWith(content, 0xff, 0xd8, 0xff);
            case "gif" -> startsWith(content, "GIF87a".getBytes()) || startsWith(content, "GIF89a".getBytes());
            case "webp" -> startsWith(content, "RIFF".getBytes()) && content.length >= 12
                    && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
            case "pdf" -> startsWith(content, "%PDF-".getBytes());
            case "svg" -> hasSafeXmlRoot(content, "svg");
            case "drawio" -> hasSafeXmlRoot(content, "mxfile", "mxGraphModel", "diagram");
            case "puml" -> !new String(content).isBlank() && !new String(content).contains("\u0000");
            default -> false;
        };
    }

    private String detectedBinaryExtension(byte[] content) {
        if (startsWith(content, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) return "png";
        if (startsWith(content, 0xff, 0xd8, 0xff)) return "jpg";
        if (startsWith(content, "GIF87a".getBytes()) || startsWith(content, "GIF89a".getBytes())) return "gif";
        if (startsWith(content, "RIFF".getBytes()) && content.length >= 12
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') return "webp";
        if (startsWith(content, "%PDF-".getBytes())) return "pdf";
        return null;
    }

    private boolean hasSafeXmlRoot(byte[] content, String... allowedRoots) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
            String rootName = document.getDocumentElement().getLocalName();
            if (rootName == null) {
                rootName = document.getDocumentElement().getNodeName();
            }
            for (String allowedRoot : allowedRoots) {
                if (allowedRoot.equals(rootName)) {
                    return true;
                }
            }
            return false;
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            return false;
        }
    }

    private String saveWithUniqueName(String extension, byte[] content) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String fileName = UUID.randomUUID() + "." + extension;
            Path target = resolve(fileName);
            try {
                Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return fileName;
            } catch (FileAlreadyExistsException ignored) {
                // Generate a fresh UUID instead of overwriting another request's file.
            } catch (IOException exception) {
                throw new SecurityReviewAgentException("Could not save upload", exception);
            }
        }
        throw new SecurityReviewAgentException("Could not allocate a unique upload name");
    }

    private boolean startsWith(byte[] content, int... prefix) {
        if (content.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if ((content[index] & 0xff) != prefix[index]) return false;
        }
        return true;
    }

    private boolean startsWith(byte[] content, byte[] prefix) {
        if (content.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (content[index] != prefix[index]) return false;
        }
        return true;
    }
}
