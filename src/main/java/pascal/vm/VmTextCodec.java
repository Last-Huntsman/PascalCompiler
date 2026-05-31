package pascal.vm;

import java.util.ArrayList;
import java.util.List;

public final class VmTextCodec {
    private VmTextCodec() {
    }

    public static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    public static String unquote(String token) {
        if (token.length() < 2 || token.charAt(0) != '"' || token.charAt(token.length() - 1) != '"') {
            return token;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < token.length() - 1; i++) {
            char ch = token.charAt(i);
            if (ch == '\\' && i + 1 < token.length() - 1) {
                char next = token.charAt(++i);
                switch (next) {
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case '\\' -> result.append('\\');
                    case '"' -> result.append('"');
                    default -> result.append(next);
                }
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    public static List<String> tokenize(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                current.append(ch);
                if (ch == '\\' && i + 1 < line.length()) {
                    current.append(line.charAt(++i));
                } else if (ch == '"') {
                    quoted = false;
                }
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
            if (ch == '"') {
                quoted = true;
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
