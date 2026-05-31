package pascal.vm;

import pascal.semantic.Type;

public final class VmTypeCodec {
    private VmTypeCodec() {
    }

    public static String encode(Type type) {
        if (type == null) {
            return "void";
        }
        if (type == Type.INTEGER) {
            return "integer";
        }
        if (type == Type.DOUBLE) {
            return "double";
        }
        if (type == Type.BOOLEAN) {
            return "boolean";
        }
        if (type == Type.CHAR) {
            return "char";
        }
        if (type == Type.STRING) {
            return "string";
        }
        if (type == Type.VOID) {
            return "void";
        }
        if (type == Type.ERROR) {
            return "error";
        }
        return "array(" + encode(type.elementType()) + "," + type.lowerBound() + "," + type.upperBound() + ")";
    }

    public static Type decode(String text) {
        Parser parser = new Parser(text);
        return parser.parseType();
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text;
        }

        private Type parseType() {
            if (match("integer")) return Type.INTEGER;
            if (match("double")) return Type.DOUBLE;
            if (match("boolean")) return Type.BOOLEAN;
            if (match("char")) return Type.CHAR;
            if (match("string")) return Type.STRING;
            if (match("void")) return Type.VOID;
            if (match("error")) return Type.ERROR;
            if (match("array(")) {
                Type elementType = parseType();
                expect(',');
                int lower = parseInt();
                expect(',');
                int upper = parseInt();
                expect(')');
                return Type.array(elementType, lower, upper);
            }
            throw new IllegalArgumentException("Cannot parse VM type: " + text);
        }

        private int parseInt() {
            int start = index;
            if (peek() == '-') {
                index++;
            }
            while (Character.isDigit(peek())) {
                index++;
            }
            return Integer.parseInt(text.substring(start, index));
        }

        private boolean match(String prefix) {
            if (!text.startsWith(prefix, index)) {
                return false;
            }
            index += prefix.length();
            return true;
        }

        private void expect(char expected) {
            if (peek() != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "' in " + text);
            }
            index++;
        }

        private char peek() {
            if (index >= text.length()) {
                return '\0';
            }
            return text.charAt(index);
        }
    }
}
