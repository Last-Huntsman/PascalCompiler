package pascal.semantic;

import java.util.Objects;

public final class Type {
    public static final Type INTEGER = new Type(Kind.INTEGER, "integer", null, 0, 0);
    public static final Type CHAR = new Type(Kind.CHAR, "char", null, 0, 0);
    public static final Type BOOLEAN = new Type(Kind.BOOLEAN, "boolean", null, 0, 0);
    public static final Type STRING = new Type(Kind.STRING, "string", null, 0, 0);
    public static final Type DOUBLE = new Type(Kind.DOUBLE, "double", null, 0, 0);
    public static final Type VOID = new Type(Kind.VOID, "void", null, 0, 0);
    public static final Type ERROR = new Type(Kind.ERROR, "<error>", null, 0, 0);

    public enum Kind {
        INTEGER, CHAR, BOOLEAN, STRING, DOUBLE, ARRAY, VOID, ERROR
    }

    private final Kind kind;
    private final String name;
    private final Type elementType;
    private final int lowerBound;
    private final int upperBound;

    private Type(Kind kind, String name, Type elementType, int lowerBound, int upperBound) {
        this.kind = kind;
        this.name = name;
        this.elementType = elementType;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public static Type array(Type elementType, int lowerBound, int upperBound) {
        return new Type(Kind.ARRAY, "array", elementType, lowerBound, upperBound);
    }

    public Kind kind() {
        return kind;
    }

    public Type elementType() {
        return elementType;
    }

    public int lowerBound() {
        return lowerBound;
    }

    public int upperBound() {
        return upperBound;
    }

    public int arraySize() {
        return upperBound - lowerBound + 1;
    }

    public boolean isNumeric() {
        return kind == Kind.INTEGER || kind == Kind.DOUBLE;
    }

    public boolean isScalar() {
        return kind == Kind.INTEGER || kind == Kind.DOUBLE || kind == Kind.BOOLEAN
                || kind == Kind.CHAR || kind == Kind.STRING;
    }

    public boolean canAssignFrom(Type source) {
        if (this == ERROR || source == ERROR) return true;
        if (equals(source)) return true;
        return kind == Kind.DOUBLE && source.kind == Kind.INTEGER;
    }

    public String displayName() {
        if (kind == Kind.ARRAY) {
            return "array[" + lowerBound + ".." + upperBound + "] of " + elementType.displayName();
        }
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Type other)) return false;
        return kind == other.kind
                && lowerBound == other.lowerBound
                && upperBound == other.upperBound
                && Objects.equals(elementType, other.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, elementType, lowerBound, upperBound);
    }
}
