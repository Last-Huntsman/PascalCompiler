package pascal.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Symbol {
    public enum Kind {
        VARIABLE, CONSTANT, PARAMETER, FUNCTION, PROCEDURE
    }

    private final String name;
    private final Kind kind;
    private final Type type;
    private final boolean byReference;
    private final List<Symbol> parameters;

    public Symbol(String name, Kind kind, Type type) {
        this(name, kind, type, false, List.of());
    }

    public Symbol(String name, Kind kind, Type type, boolean byReference) {
        this(name, kind, type, byReference, List.of());
    }

    public Symbol(String name, Kind kind, Type type, boolean byReference, List<Symbol> parameters) {
        this.name = name;
        this.kind = kind;
        this.type = type;
        this.byReference = byReference;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public Type type() {
        return type;
    }

    public boolean byReference() {
        return byReference;
    }

    public List<Symbol> parameters() {
        return parameters;
    }

    public boolean isRoutine() {
        return kind == Kind.FUNCTION || kind == Kind.PROCEDURE;
    }

    public boolean isWritable() {
        return kind == Kind.VARIABLE || kind == Kind.PARAMETER;
    }
}
