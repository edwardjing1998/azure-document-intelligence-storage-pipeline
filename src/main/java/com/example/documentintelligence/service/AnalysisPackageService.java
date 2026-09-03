package com.example.documentintelligence.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AnalysisPackageService {
    private static final Pattern FIRST_FIGURE = Pattern.compile(
            "<figure>.*?</figure>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public byte[] create(byte[] original, String originalName, DocumentAnalysisResult analysis)
            throws IOException {
        String markdown = connectFigures(analysis);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            add(zip, "content.md", markdown.getBytes(StandardCharsets.UTF_8));
            add(zip, "original/" + safeName(originalName), original);
            for (ExtractedFigure figure : analysis.figures()) {
                add(zip, "figures/" + figure.fileName(), figure.content());
            }
            zip.finish();
            return bytes.toByteArray();
        }
    }

    public String connectFigures(DocumentAnalysisResult analysis) {
        String markdown = analysis.markdown();
        if (analysis.figures().isEmpty()) {
            return markdown + "\n\n> No figure was detected. Review the original image.\n";
        }

        ExtractedFigure first = analysis.figures().get(0);
        String link = "![Detected geometry diagram](figures/" + first.fileName() + ")";
        Matcher matcher = FIRST_FIGURE.matcher(markdown);
        if (matcher.find()) {
            markdown = matcher.replaceFirst(Matcher.quoteReplacement(link));
        } else {
            markdown += "\n\n## Detected geometry diagram\n\n" + link + "\n";
        }

        for (int index = 1; index < analysis.figures().size(); index++) {
            ExtractedFigure figure = analysis.figures().get(index);
            markdown += "\n\n![Detected figure " + (index + 1) + "](figures/"
                    + figure.fileName() + ")\n";
        }
        return markdown;
    }

    private void add(ZipOutputStream zip, String path, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content);
        zip.closeEntry();
    }

    private String safeName(String value) {
        if (value == null || value.isBlank()) {
            return "source.png";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
