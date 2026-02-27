package com.app.LogAnalyser.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileReader;
import java.util.*;

@Slf4j
@Service
public class ErrorTriggerService {

    public void generateNullPointer() {
        try {
            String value = null;
            value.length();
        } catch (NullPointerException e) {
            log.error("NullPointerException", e);
            throw e;
        } catch (Exception e) {
            log.error("Exception", e);
        }
    }

    public void generateArrayOutOfBounds() {
        try {
            int[] arr = {1, 2, 3};
            int x = arr[10];
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("ArrayIndexOutOfBoundsException", e);
            throw e;
        } catch (Exception e) {
            log.error("Exception", e);
        }
    }

    public void generateNumberFormat() {
        try {
            Integer.parseInt("abc123");
        } catch (NumberFormatException e) {
            log.error("NumberFormatException", e);
            throw e;
        } catch (Exception e) {
            log.error("Exception", e);
        }
    }

    public void generateFileNotFound() {
        try {
            new FileReader("C:/nonexistent/missing.txt");
        } catch (Exception e) {
            log.error("FileNotFoundException", e);
            throw new RuntimeException(e);
        }
    }

    public void generateClassCast() {
        try {
            Object value = Integer.valueOf(42);
            String result = (String) value;
        } catch (ClassCastException e) {
            log.error("ClassCastException", e);
            throw e;
        } catch (Exception e) {
            log.error("Exception", e);
        }
    }

    public void generateIllegalArgument() {
        try {
            validateAge(-5);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException", e);
            throw e;
        } catch (Exception e) {
            log.error("Exception", e);
        }
    }

    public void generateStackOverflow() {
        try {
            recursiveMethod(0);
        } catch (StackOverflowError e) {
            log.error("StackOverflowError", e);
            throw e;
        } catch (Exception e) {
            log.error("Exception", e);
        }
    }

    public void generateConcurrentModification() {
        try {
            List<String> list = new ArrayList<>(
                    Arrays.asList("a", "b", "c"));
            for (String item : list) {
                list.remove(item);
            }
        } catch (ConcurrentModificationException e) {
            log.error("ConcurrentModificationException", e);
            throw e;
        } catch (Exception e) {
            log.error("Exception", e);
        }
    }

    public Map<String, String> generateAll() {
        Map<String, String> results = new LinkedHashMap<>();

        try { generateNullPointer(); }
        catch (Exception | Error e) {
            results.put("NullPointerException", "triggered");
        }

        try { generateArrayOutOfBounds(); }
        catch (Exception | Error e) {
            results.put("ArrayIndexOutOfBoundsException", "triggered");
        }

        try { generateNumberFormat(); }
        catch (Exception | Error e) {
            results.put("NumberFormatException", "triggered");
        }

        try { generateFileNotFound(); }
        catch (Exception | Error e) {
            results.put("FileNotFoundException", "triggered");
        }

        try { generateClassCast(); }
        catch (Exception | Error e) {
            results.put("ClassCastException", "triggered");
        }

        try { generateIllegalArgument(); }
        catch (Exception | Error e) {
            results.put("IllegalArgumentException", "triggered");
        }

        try { generateStackOverflow(); }
        catch (Exception | Error e) {
            results.put("StackOverflowError", "triggered");
        }

        try { generateConcurrentModification(); }
        catch (Exception | Error e) {
            results.put("ConcurrentModificationException", "triggered");
        }

        results.put("status", "All errors triggered!");
        return results;
    }

    private void validateAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative. Received: " + age);
        }
    }

    private int recursiveMethod(int count) {
        return recursiveMethod(count + 1);
    }
}