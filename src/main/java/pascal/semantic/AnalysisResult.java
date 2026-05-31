package pascal.semantic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnalysisResult {
    private final Scope globalScope;
    private final List<String> errors;

    public AnalysisResult(Scope globalScope, List<String> errors) {
        this.globalScope = globalScope;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public Scope globalScope() {
        return globalScope;
    }

    public List<String> errors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
