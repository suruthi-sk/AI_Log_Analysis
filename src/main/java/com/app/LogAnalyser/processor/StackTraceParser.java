package com.app.LogAnalyser.processor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StackTraceParser {
    private static final Pattern FRAME_PATTERN = Pattern.compile("^\\s*at\\s+([\\w$.]+)\\.(\\w+)\\(([\\w$.]+\\.java):(\\d+)\\)");

    @Value("${git.source.package-prefix:com.app}")
    private String packagePrefix;

    @Value("${git.source.root:src/main/java}")
    private String sourceRoot;

    @Value("${git.blame.max-trace-depth:5}")
    private int maxTraceDepth;

    public ParsedFrame parse(String stackTrace) {
        if(stackTrace == null || stackTrace.isBlank()) {
            log.warn("Stack trace is null or empty.");
            return null;
        }

        for(String line : stackTrace.split("\n")) {
            Matcher matcher = FRAME_PATTERN.matcher(line.trim());

            if(matcher.find()) {
                String fullClassName = matcher.group(1);
                int lineNumber = Integer.parseInt(matcher.group(4));

                if(!fullClassName.startsWith(packagePrefix)) {
                    log.debug("Skipping frame — not in package prefix '{}': {}", packagePrefix, fullClassName);
                    continue;
                }

                String filePath = toFilePath(fullClassName);
                log.info("Parsed stack frame -> class: {} | line: {} | path: {}", fullClassName, lineNumber, filePath);
                return new ParsedFrame(fullClassName, lineNumber, filePath);
            }
        }

        log.warn("No matching frame found in stack trace for package prefix '{}'", packagePrefix);
        return null;
    }

    public List<ParsedFrame> parseAllFrames(String stackTrace) {
        List<ParsedFrame> frames = new ArrayList<>();

        if(stackTrace == null || stackTrace.isBlank()) {
            log.warn("Stack trace is null or empty.");
            return frames;
        }

        for(String line : stackTrace.split("\n")) {
            if(frames.size() >= maxTraceDepth) {
                log.info("Frames formed for '{}' lines and breaking now", maxTraceDepth);
                break;
            }
            Matcher matcher = FRAME_PATTERN.matcher(line.trim());

            if(matcher.find()) {
                String fullClassName = matcher.group(1);
                int lineNumber = Integer.parseInt(matcher.group(4));

                if(!fullClassName.startsWith(packagePrefix)) {
                    log.debug("Skipping frame — not in package prefix '{}': {}", packagePrefix, fullClassName);
                    continue;
                }

                String filePath = toFilePath(fullClassName);
                frames.add(new ParsedFrame(fullClassName, lineNumber, filePath));
                log.info("Collected frame -> class: {} | line: {} | path: {}", fullClassName, lineNumber, filePath);
            }
        }

        return frames;
    }

    private String toFilePath(String className) {
        String outerClass = className.contains("$")? className.substring(0, className.indexOf('$')): className;
        String relativePath = outerClass.replace('.', '/') + ".java";
        return sourceRoot + "/" + relativePath;
    }

    public static class ParsedFrame {
        public final String className;
        public final int lineNumber;
        public final String filePath;

        public ParsedFrame(String className, int lineNumber, String filePath) {
            this.className  = className;
            this.lineNumber = lineNumber;
            this.filePath   = filePath;
        }
    }
}