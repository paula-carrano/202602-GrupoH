package com.cbo.players.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.security.Principal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditParameterSanitizer {

    public static final String REDACTED = "[REDACTED]";
    public static final int MAX_OUTPUT_LENGTH = 500;
    private static final int MAX_COLLECTION_PREVIEW = 10;
    private static final int MAX_DEPTH = 4;
    private static final String MORE_SUFFIX = " more";

    private static final List<String> SENSITIVE_TOKENS = List.of(
            "password",
            "passwd",
            "secret",
            "token",
            "apikey",
            "api_key",
            "authorization",
            "credential",
            "jwt",
            "key");

    private final ObjectMapper mapper;

    public AuditParameterSanitizer() {
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String sanitize(String[] parameterNames, Object[] args) {
        if (args == null || args.length == 0) {
            return "{}";
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            String name = (parameterNames != null && i < parameterNames.length && parameterNames[i] != null)
                    ? parameterNames[i]
                    : "arg" + i;
            Object arg = args[i];
            if (isOmittedType(arg)) {
                continue;
            }
            if (isSensitiveName(name)) {
                result.put(name, REDACTED);
            } else {
                result.put(name, sanitizeValue(arg, 0));
            }
        }
        String formatted = sanitizeForSingleLine(result.toString());
        if (formatted.length() > MAX_OUTPUT_LENGTH) {
            return formatted.substring(0, MAX_OUTPUT_LENGTH) + "...[TRUNCATED]";
        }
        return formatted;
    }

    public boolean isSensitiveName(String fieldName) {
        if (fieldName == null || fieldName.isBlank()) {
            return false;
        }
        String lower = fieldName.toLowerCase(Locale.ROOT);
        for (String token : SENSITIVE_TOKENS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Object sanitizeValue(Object value, int depth) {
        if (value == null) {
            return "null";
        }
        if (depth >= MAX_DEPTH) {
            return "[MAX_DEPTH]";
        }
        if (isOmittedType(value)) {
            return "[" + value.getClass().getSimpleName() + "]";
        }
        if (isSimpleScalar(value)) {
            return sanitizeForSingleLine(String.valueOf(value));
        }
        if (value instanceof Map<?, ?> map) {
            return sanitizeMap(map, depth + 1);
        }
        if (value instanceof Collection<?> collection) {
            return sanitizeCollection(collection, depth + 1);
        }
        if (value.getClass().isArray()) {
            return sanitizeArray(value, depth + 1);
        }
        return sanitizeComplexObject(value, depth + 1);
    }

    private Map<String, Object> sanitizeMap(Map<?, ?> map, int depth) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (count++ >= MAX_COLLECTION_PREVIEW) {
                sanitized.put("...", "+" + (map.size() - MAX_COLLECTION_PREVIEW) + MORE_PREFIX);
                break;
            }
            String key = sanitizeForSingleLine(String.valueOf(entry.getKey()));
            if (isSensitiveName(key)) {
                sanitized.put(key, REDACTED);
            } else {
                sanitized.put(key, sanitizeValue(entry.getValue(), depth));
            }
        }
        return sanitized;
    }

    private List<Object> sanitizeCollection(Collection<?> collection, int depth) {
        List<Object> sanitized = new ArrayList<>();
        int count = 0;
        for (Object item : collection) {
            if (count++ >= MAX_COLLECTION_PREVIEW) {
                sanitized.add("...+" + (collection.size() - MAX_COLLECTION_PREVIEW) + MORE_PREFIX);
                break;
            }
            sanitized.add(sanitizeValue(item, depth));
        }
        return sanitized;
    }

    private List<Object> sanitizeArray(Object array, int depth) {
        int len = Array.getLength(array);
        List<Object> sanitized = new ArrayList<>();
        int limit = Math.min(len, MAX_COLLECTION_PREVIEW);
        for (int i = 0; i < limit; i++) {
            sanitized.add(sanitizeValue(Array.get(array, i), depth));
        }
        if (len > MAX_COLLECTION_PREVIEW) {
            sanitized.add("...+" + (len - MAX_COLLECTION_PREVIEW) + MORE_PREFIX);
        }
        return sanitized;
    }

    private Object sanitizeComplexObject(Object value, int depth) {
        try {
            Map<String, Object> asMap = mapper.convertValue(value, new TypeReference<>() {
            });
            return sanitizeMap(asMap, depth);
        } catch (IllegalArgumentException ex) {
            return "[" + value.getClass().getSimpleName() + "]";
        }
    }

    private static boolean isSimpleScalar(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof TemporalAccessor;
    }

    private static boolean isOmittedType(Object value) {
        return value instanceof ServletRequest
                || value instanceof ServletResponse
                || value instanceof Principal
                || value instanceof Authentication
                || value instanceof UserDetails
                || value instanceof BindingResult
                || value instanceof InputStream
                || value instanceof OutputStream
                || value instanceof MultipartFile;
    }

    private static String sanitizeForSingleLine(String input) {
        if (input == null) {
            return "null";
        }
        return input.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }
}
