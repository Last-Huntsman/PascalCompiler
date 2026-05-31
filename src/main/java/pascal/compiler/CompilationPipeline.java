package pascal.compiler;

import pascal.ast.Node;
import pascal.error.LexerException;
import pascal.error.ParseException;
import pascal.lexer.Lexer;
import pascal.lexer.Token;
import pascal.optimize.AstOptimizer;
import pascal.parser.Parser;
import pascal.semantic.AnalysisResult;
import pascal.semantic.Scope;
import pascal.semantic.SemanticAnalyzer;

import java.util.ArrayList;
import java.util.List;

public class CompilationPipeline {
    private final SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
    private final AstOptimizer optimizer = new AstOptimizer();

    public CompilationResult compile(String source, int optimizationLevel) throws LexerException, ParseException {
        Lexer lexer = new Lexer(source);
        List<Token> tokens = lexer.tokenize();

        Parser parser = new Parser(tokens);
        Node.ProgramNode parsedAst = parser.parseProgram();
        List<String> syntaxDiagnostics = new ArrayList<>(parser.getErrors());

        AnalysisResult semanticResult = new AnalysisResult(new Scope(null), List.of());
        Node.ProgramNode program = parsedAst;
        if (syntaxDiagnostics.isEmpty()) {
            semanticResult = semanticAnalyzer.analyze(parsedAst);
            if (!semanticResult.hasErrors() && optimizationLevel > 0) {
                program = optimizer.optimize(parsedAst);
            }
        }

        return new CompilationResult(tokens, parsedAst, program, syntaxDiagnostics, semanticResult, optimizationLevel);
    }
}
