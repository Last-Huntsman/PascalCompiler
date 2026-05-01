package pascal;

import pascal.ast.Node;
import pascal.error.LexerException;
import pascal.error.ParseException;
import pascal.lexer.Lexer;
import pascal.lexer.Token;
import pascal.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Использование: pascal.Main [--tokens] <файл.pas>");
            System.exit(1);
        }

        boolean showTokens = false;
        String filename = args[args.length - 1];
        for (String arg : args) {
            if (arg.equals("--tokens")) showTokens = true;
        }

        String source;
        try {
            source = Files.readString(Paths.get(filename));
        } catch (IOException e) {
            System.err.println("Ошибка чтения файла: " + filename);
            System.exit(1);
            return;
        }

        System.out.println("Файл: " + filename);
        System.out.println();


        System.out.println("─── Лексический анализ ───");
        List<Token> tokens;
        try {
            Lexer lexer = new Lexer(source);
            tokens = lexer.tokenize();
            System.out.println("✓ Токенизация успешна. Токенов: " + tokens.size());
        } catch (LexerException e) {
            System.err.println("✗ " + e.getMessage());
            System.exit(1);
            return;
        }

        if (showTokens) {
            System.out.println();
            System.out.println("Токены:");
            for (Token t : tokens) System.out.println("  " + t);
        }


        System.out.println();
        System.out.println("─── Синтаксический анализ ───");
        Parser parser = new Parser(tokens);
        Node.ProgramNode ast = null;
        try {
            ast = parser.parseProgram();
        } catch (ParseException e) {
            System.err.println("✗ " + e.getMessage());
        }

        List<String> errors = parser.getErrors();
        if (!errors.isEmpty()) {
            System.out.println("Диагностика ошибок (" + errors.size() + "):");
            for (String err : errors) System.err.println("  ✗ " + err);
        }


        if (ast != null) {
            System.out.println("✓ AST построено успешно.");
            System.out.println();
            System.out.println("─── AST-дерево ───");
            ast.printTree();
        } else {
            System.err.println("✗ Не удалось построить AST из-за синтаксических ошибок.");
            System.exit(1);
        }
    }
}
