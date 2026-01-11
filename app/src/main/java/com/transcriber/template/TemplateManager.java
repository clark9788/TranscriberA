package com.transcriber.template;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for loading and applying transcription templates from Android assets.
 */
public class TemplateManager {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}");

    /**
     * Load all template files from the "templates" asset directory.
     *
     * @param context The Android context to access assets.
     * @return A map of template name to template content.
     */
    public static Map<String, String> loadTemplates(Context context) {
        Map<String, String> templates = new HashMap<>();
        AssetManager assetManager = context.getAssets();
        try {
            String[] templateFiles = assetManager.list("templates");
            if (templateFiles != null) {
                for (String fileName : templateFiles) {
                    if (fileName.endsWith(".txt")) {
                        String content = readAsset(assetManager, "templates/" + fileName);
                        String name = fileName.substring(0, fileName.lastIndexOf('.'));
                        templates.put(name, content);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load templates: " + e.getMessage());
        }
        return templates;
    }

    /**
     * Apply a template by replacing placeholders.
     *
     * @param templateContent The raw string content of the template.
     * @param transcript      The transcription text.
     * @param context         Additional key-value pairs for placeholders.
     * @return The processed template string.
     */
    public static String applyTemplate(String templateContent, String transcript, Map<String, String> context) {
        Map<String, String> replacements = new HashMap<>(context != null ? context : new HashMap<>());
        replacements.put("TRANSCRIPT", transcript != null ? transcript : "");

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(templateContent);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = replacements.getOrDefault(key, "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Helper to read a text file from the assets folder.
     */
    private static String readAsset(AssetManager assetManager, String path) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = assetManager.open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
