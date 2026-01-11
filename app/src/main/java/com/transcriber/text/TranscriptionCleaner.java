package com.transcriber.text;

import com.transcriber.config.Config;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Transcription text cleaning utilities.
 */
public class TranscriptionCleaner {
    
    private static final Pattern MULTIPLE_SPACES = Pattern.compile(" +");
    private static final Pattern SPACE_BEFORE_PUNCTUATION = Pattern.compile(" +([.,!?;:])");
    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\n{3,}");
    
    /**
     * Remove filler words from transcription text.
     * 
     * @param text The transcription text to clean
     * @param fillerWords Optional list of filler words. If null, uses Config.FILLER_WORDS
     * @return Cleaned text with filler words removed
     */
    public static String removeFillerWords(String text, List<String> fillerWords) {
        if (fillerWords == null) {
            fillerWords = Config.FILLER_WORDS;
        }
        
        if (text == null || text.trim().isEmpty() || fillerWords.isEmpty()) {
            return text != null ? text : "";
        }
        
        String[] lines = text.split("\n");
        StringBuilder cleanedLines = new StringBuilder();
        
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                cleanedLines.append(line).append("\n");
                continue;
            }
            
            String[] words = line.split(" ");
            StringBuilder cleanedWords = new StringBuilder();
            
            int i = 0;
            while (i < words.length) {
                String word = words[i];
                String wordLower = stripPunctuation(word.toLowerCase());
                String originalWord = words[i];
                
                boolean isFiller = false;
                
                for (String filler : fillerWords) {
                    String fillerLower = filler.toLowerCase();
                    
                    // Special handling for "a" - only remove if it's standalone and not followed by a noun/article context
                    if (wordLower.equals("a") && fillerLower.equals("a")) {
                        boolean isAtStart = i == 0 || cleanedWords.length() == 0;
                        if (!isAtStart) {
                            String prevText = cleanedWords.toString().toLowerCase();
                            isAtStart = prevText.endsWith(".") || prevText.endsWith("!") || prevText.endsWith("?") 
                                    || prevText.endsWith(";") || prevText.endsWith(":");
                        }
                        
                        boolean nextIsFiller = false;
                        if (i < words.length - 1) {
                            String nextWordCheck = stripPunctuation(words[i + 1].toLowerCase());
                            for (String fw : fillerWords) {
                                if (!fw.equalsIgnoreCase("a") && nextWordCheck.equals(fw.toLowerCase())) {
                                    nextIsFiller = true;
                                    break;
                                }
                            }
                        }
                        
                        if (isAtStart || nextIsFiller) {
                            isFiller = true;
                            break;
                        }
                        // Otherwise, keep "a" as it's likely an article
                        continue;
                    }
                    
                    // Exact match (case-insensitive, ignoring punctuation)
                    if (wordLower.equals(fillerLower)) {
                        isFiller = true;
                        break;
                    }
                    
                    // Check for multi-word fillers (like "you know")
                    if (fillerLower.contains(" ") && i < words.length - 1) {
                        String nextWord = stripPunctuation(words[i + 1].toLowerCase());
                        if ((wordLower + " " + nextWord).equals(fillerLower)) {
                            isFiller = true;
                            i++; // Skip next word too
                            break;
                        }
                    }
                }
                
                if (!isFiller) {
                    if (cleanedWords.length() > 0) {
                        cleanedWords.append(" ");
                    }
                    cleanedWords.append(originalWord);
                }
                
                i++;
            }
            
            if (cleanedWords.length() > 0) {
                cleanedLines.append(cleanedWords);
            }
            cleanedLines.append("\n");
        }
        
        String result = cleanedLines.toString();
        
        // Clean up multiple spaces
        result = MULTIPLE_SPACES.matcher(result).replaceAll(" ");
        // Clean up spaces before punctuation
        result = SPACE_BEFORE_PUNCTUATION.matcher(result).replaceAll("$1");
        // Clean up multiple newlines (keep at most 2)
        result = MULTIPLE_NEWLINES.matcher(result).replaceAll("\n\n");
        
        return result.trim();
    }
    
    /**
     * Remove filler words using the default filler word list from Config.
     */
    public static String removeFillerWords(String text) {
        return removeFillerWords(text, null);
    }
    
    /**
     * Strip punctuation from a word for comparison purposes.
     */
    private static String stripPunctuation(String word) {
        return word.replaceAll("[.,!?;:()\\[\\]{}'\"\\s]+", "").trim();
    }
}


