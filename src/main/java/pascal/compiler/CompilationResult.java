package pascal.compiler;

import pascal.ast.Node;
import pascal.lexer.Token;
import pascal.semantic.AnalysisResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompilationResult {
    private final List<Token> tokens;
    private final Node.ProgramNode parsedAst;
    private final Node.ProgramNode program;
    private final List<String> syntaxDiagnostics;
    private final AnalysisResult semanticResult;
    private final int optimizationLevel;

    public CompilationResult(List<Token> tokens,
                             Node.ProgramNode parsedAst,
                             Node.ProgramNode program,
                             List<String> syntaxDiagnostics,
                             AnalysisResult semanticResult,
                             int optimizationLevel) {
        this.tokens = Collections.unmodifiableList(new ArrayList<>(tokens));
        this.parsedAst = parsedAst;
        this.program = program;
        this.syntaxDiagnostics = Collections.unmodifiableList(new ArrayList<>(syntaxDiagnostics));
        this.semanticResult = semanticResult;
        this.optimizationLevel = optimizationLevel;
    }

    public List<Token> tokens() {
        return tokens;
    }

    public Node.ProgramNode parsedAst() {
        return parsedAst;
    }

    public Node.ProgramNode program() {
        return program;
    }

    public List<String> syntaxDiagnostics() {
        return syntaxDiagnostics;
    }

    public AnalysisResult semanticResult() {
        return semanticResult;
    }

    public int optimizationLevel() {
        return optimizationLevel;
    }

    public boolean hasSyntaxDiagnostics() {
        return !syntaxDiagnostics.isEmpty();
    }

    public boolean hasSemanticErrors() {
        return semanticResult != null && semanticResult.hasErrors();
    }
}
