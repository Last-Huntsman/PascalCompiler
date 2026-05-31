package pascal.semantic;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class Scope {
    private final Scope parent;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Scope parent() {
        return parent;
    }

    public boolean define(Symbol symbol) {
        String key = normalize(symbol.name());
        if (symbols.containsKey(key)) return false;
        symbols.put(key, symbol);
        return true;
    }

    public Symbol resolve(String name) {
        String key = normalize(name);
        for (Scope scope = this; scope != null; scope = scope.parent) {
            Symbol symbol = scope.symbols.get(key);
            if (symbol != null) return symbol;
        }
        return null;
    }

    public Symbol resolveLocal(String name) {
        return symbols.get(normalize(name));
    }

    public Collection<Symbol> localSymbols() {
        return symbols.values();
    }

    public static String normalize(String name) {
        return name.toLowerCase();
    }
}
