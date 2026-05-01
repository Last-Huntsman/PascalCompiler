package pascal.parser;

import pascal.ast.Node;
import pascal.error.ParseException;
import pascal.lexer.Token;
import pascal.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;


public class Parser {
    private final List<Token> tokens;
    private int pos;
    private final List<String> errors = new ArrayList<>();

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    public List<String> getErrors() { return errors; }



    private Token current() { return tokens.get(pos); }

    private Token peek(int offset) {
        int idx = pos + offset;
        if (idx >= tokens.size()) return tokens.get(tokens.size() - 1);
        return tokens.get(idx);
    }

    private Token consume() { return tokens.get(pos++); }

    private Token expect(TokenType type) throws ParseException {
        Token t = current();
        if (t.type != type) {
            throw new ParseException(
                "Ожидается " + type + ", получено " + t.type + " ('" + t.value + "')",
                t.line, t.column);
        }
        return consume();
    }

    private boolean check(TokenType type) { return current().type == type; }

    private boolean match(TokenType... types) {
        for (TokenType t : types) {
            if (check(t)) { consume(); return true; }
        }
        return false;
    }

    /** Синхронизация после ошибки: пропускаем токены до ближайшего из stopSet */
    private void synchronize(TokenType... stopSet) {
        while (!check(TokenType.EOF)) {
            for (TokenType t : stopSet) {
                if (check(t)) return;
            }
            consume();
        }
    }



    public Node.ProgramNode parseProgram() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.PROGRAM);
        Token nameToken = expect(TokenType.IDENTIFIER);
        expect(TokenType.SEMICOLON);

        List<Node> decls = parseDeclarations();// до переменных
        Node.BlockNode body = parseBlock();
        expect(TokenType.DOT);
        if (!check(TokenType.EOF)) {
            errors.add(String.format("Предупреждение [%d:%d]: текст после точки будет проигнорирован",
                    current().line, current().column));
        }
        return new Node.ProgramNode(nameToken.value, decls, body, line, col);
    }



    private List<Node> parseDeclarations() throws ParseException {
        List<Node> decls = new ArrayList<>();
        while (true) {
            if (check(TokenType.VAR)) {
                decls.add(parseVarSection());
            } else if (check(TokenType.CONST)) {
                decls.add(parseConstSection());
            } else if (check(TokenType.PROCEDURE)) {
                decls.add(parseProcedure());
            } else if (check(TokenType.FUNCTION)) {
                decls.add(parseFunction());
            } else {
                break;
            }
        }
        return decls;
    }

    private Node.VarSectionNode parseVarSection() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.VAR);
        List<Node.VarDeclNode> decls = new ArrayList<>();
        while (check(TokenType.IDENTIFIER)) {
            decls.add(parseVarDecl());
            expect(TokenType.SEMICOLON);
        }
        if (decls.isEmpty()) {
            errors.add(String.format("Синтаксическая ошибка [%d:%d]: пустая секция var", line, col));
        }
        return new Node.VarSectionNode(decls, line, col);
    }

    private Node.VarDeclNode parseVarDecl() throws ParseException {
        int line = current().line, col = current().column;
        List<String> names = parseIdentList();
        expect(TokenType.COLON);
        Node type = parseTypeSpec();
        return new Node.VarDeclNode(names, type, line, col);
    }

    private Node.ConstSectionNode parseConstSection() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.CONST);
        List<Node.ConstDeclNode> decls = new ArrayList<>();
        while (check(TokenType.IDENTIFIER)) {
            int dl = current().line, dc = current().column;
            String name = consume().value;
            expect(TokenType.EQ);
            Node value = parseExpression();
            expect(TokenType.SEMICOLON);
            decls.add(new Node.ConstDeclNode(name, value, dl, dc));
        }
        return new Node.ConstSectionNode(decls, line, col);
    }

    private List<String> parseIdentList() throws ParseException {
        List<String> names = new ArrayList<>();
        names.add(expect(TokenType.IDENTIFIER).value);
        while (check(TokenType.COMMA)) {
            consume();
            names.add(expect(TokenType.IDENTIFIER).value);
        }
        return names;
    }


    private Node parseTypeSpec() throws ParseException {
        int line = current().line, col = current().column;
        if (check(TokenType.ARRAY)) {
            return parseArrayType(line, col);
        }
        return parseSimpleType();
    }

    private Node.SimpleTypeNode parseSimpleType() throws ParseException {
        int line = current().line, col = current().column;
        Token t = current();
        switch (t.type) {
            case INTEGER: case CHAR: case BOOLEAN: case STRING: case DOUBLE:
                consume();
                return new Node.SimpleTypeNode(t.value, line, col);
            case IDENTIFIER:
                consume();
                return new Node.SimpleTypeNode(t.value, line, col);
            default:
                throw new ParseException("Ожидается тип данных, получено '" + t.value + "'", line, col);
        }
    }

    private Node.ArrayTypeNode parseArrayType(int line, int col) throws ParseException {
        expect(TokenType.ARRAY);
        expect(TokenType.LBRACKET);
        Node from = parseExpression();
        expect(TokenType.DOTDOT);
        Node to = parseExpression();
        expect(TokenType.RBRACKET);
        expect(TokenType.OF);
        Node elementType = parseTypeSpec();
        return new Node.ArrayTypeNode(from, to, elementType, line, col);
    }


    private Node.ProcedureNode parseProcedure() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.PROCEDURE);
        String name = expect(TokenType.IDENTIFIER).value;
        List<Node.ParamNode> params = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            consume();
            params = parseParamList();
            expect(TokenType.RPAREN);
        }
        expect(TokenType.SEMICOLON);
        List<Node> decls = parseDeclarations();
        Node.BlockNode body = parseBlock();
        expect(TokenType.SEMICOLON);
        return new Node.ProcedureNode(name, params, decls, body, line, col);
    }

    private Node.FunctionNode parseFunction() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.FUNCTION);
        String name = expect(TokenType.IDENTIFIER).value;
        List<Node.ParamNode> params = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            consume();
            params = parseParamList();
            expect(TokenType.RPAREN);
        }
        expect(TokenType.COLON);
        Node returnType = parseTypeSpec();
        expect(TokenType.SEMICOLON);
        List<Node> decls = parseDeclarations();
        Node.BlockNode body = parseBlock();
        expect(TokenType.SEMICOLON);
        return new Node.FunctionNode(name, params, returnType, decls, body, line, col);
    }

    private List<Node.ParamNode> parseParamList() throws ParseException {
        List<Node.ParamNode> params = new ArrayList<>();
        params.add(parseParam());
        while (check(TokenType.SEMICOLON)) {
            // lookahead: если после ';' идёт идентификатор или VAR — это следующий параметр
            if (peek(1).type == TokenType.IDENTIFIER || peek(1).type == TokenType.VAR) {
                consume(); // eat ';'
                params.add(parseParam());
            } else {
                break;
            }
        }
        return params;
    }

    private Node.ParamNode parseParam() throws ParseException {
        int line = current().line, col = current().column;
        boolean isVar = false;
        if (check(TokenType.VAR)) { consume(); isVar = true; }
        List<String> names = parseIdentList();
        expect(TokenType.COLON);
        Node type = parseTypeSpec();
        return new Node.ParamNode(names, type, isVar, line, col);
    }


    private Node.BlockNode parseBlock() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.BEGIN);
        List<Node> stmts = parseStatementList();
        expect(TokenType.END);
        return new Node.BlockNode(stmts, line, col);
    }

    private List<Node> parseStatementList() throws ParseException {
        List<Node> stmts = new ArrayList<>();
        Node s = parseStatement();
        if (s != null) stmts.add(s);
        while (check(TokenType.SEMICOLON)) {
            consume();
            if (check(TokenType.END) || check(TokenType.UNTIL) || check(TokenType.EOF)) break;
            Node next = parseStatement();
            if (next != null) stmts.add(next);
        }
        return stmts;
    }

    private Node parseStatement() throws ParseException {
        Token t = current();
        try {
            switch (t.type) {
                case BEGIN:   return parseBlock();
                case IF:      return parseIf();
                case FOR:     return parseFor();
                case WHILE:   return parseWhile();
                case REPEAT:  return parseRepeat();
                case WRITE:   return parseWrite(false);
                case WRITELN: return parseWrite(true);
                case READ:    return parseRead(false);
                case READLN:  return parseRead(true);
                case BREAK:   consume(); return new Node.BreakNode(t.line, t.column);
                case CONTINUE:consume(); return new Node.ContinueNode(t.line, t.column);
                case EXIT:    consume(); return new Node.ExitNode(t.line, t.column);
                case INC: case DEC: case ABS:
                case LENGTH: case POS: case COPY:
                    return parseSysCall();
                case IDENTIFIER:
                    return parseAssignOrCall();
                case END: case UNTIL: case EOF:
                    return null; // пустой оператор
                default:
                    return null;
            }
        } catch (ParseException e) {
            errors.add(e.getMessage());
            synchronize(TokenType.SEMICOLON, TokenType.END, TokenType.ELSE, TokenType.UNTIL);
            return null;
        }
    }

    private Node parseAssignOrCall() throws ParseException {
        int line = current().line, col = current().column;
        String name = expect(TokenType.IDENTIFIER).value;

        // Array access: a[i] := ...
        Node target;
        if (check(TokenType.LBRACKET)) {
            consume();
            Node index = parseExpression();
            expect(TokenType.RBRACKET);
            target = new Node.ArrayAccessNode(new Node.IdentifierNode(name, line, col), index, line, col);
        } else {
            target = new Node.IdentifierNode(name, line, col);
        }

        if (check(TokenType.ASSIGN)) {
            consume();
            Node value = parseExpression();
            return new Node.AssignNode(target, value, line, col);
        }

        // Procedure call
        List<Node> args = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            consume();
            if (!check(TokenType.RPAREN)) {
                args = parseArgList();
            }
            expect(TokenType.RPAREN);
        }
        return new Node.ProcCallNode(name, args, line, col);
    }

    private Node parseIf() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.IF);
        Node condition = parseExpression();
        expect(TokenType.THEN);
        Node thenBranch = parseStatement();
        Node elseBranch = null;
        if (check(TokenType.ELSE)) {
            consume();
            elseBranch = parseStatement();
        }
        return new Node.IfNode(condition, thenBranch, elseBranch, line, col);
    }

    private Node parseFor() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.FOR);
        String varName = expect(TokenType.IDENTIFIER).value;
        expect(TokenType.ASSIGN);
        Node from = parseExpression();
        boolean downTo = false;
        if (check(TokenType.TO)) consume();
        else if (check(TokenType.DOWNTO)) { consume(); downTo = true; }
        else throw new ParseException("Ожидается TO или DOWNTO", current().line, current().column);
        Node to = parseExpression();
        expect(TokenType.DO);
        Node body = parseStatement();
        return new Node.ForNode(varName, from, to, downTo, body, line, col);
    }

    private Node parseWhile() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.WHILE);
        Node condition = parseExpression();
        expect(TokenType.DO);
        Node body = parseStatement();
        return new Node.WhileNode(condition, body, line, col);
    }

    private Node parseRepeat() throws ParseException {
        int line = current().line, col = current().column;
        expect(TokenType.REPEAT);
        List<Node> stmts = parseStatementList();
        expect(TokenType.UNTIL);
        Node condition = parseExpression();
        return new Node.RepeatNode(stmts, condition, line, col);
    }

    private Node parseWrite(boolean newLine) throws ParseException {
        int line = current().line, col = current().column;
        consume();
        List<Node> args = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            consume();
            args = parseArgList();
            expect(TokenType.RPAREN);
        }
        return new Node.WriteNode(newLine, args, line, col);
    }

    private Node parseRead(boolean newLine) throws ParseException {
        int line = current().line, col = current().column;
        consume();
        List<Node> args = new ArrayList<>();
        if (check(TokenType.LPAREN)) {
            consume();
            args = parseArgList();
            expect(TokenType.RPAREN);
        }
        return new Node.ReadNode(newLine, args, line, col);
    }

    private Node parseSysCall() throws ParseException {
        int line = current().line, col = current().column;
        String name = consume().value;
        expect(TokenType.LPAREN);
        List<Node> args = parseArgList();
        expect(TokenType.RPAREN);
        return new Node.SysFuncCallNode(name, args, line, col);
    }

    private List<Node> parseArgList() throws ParseException {
        List<Node> args = new ArrayList<>();
        args.add(parseExpression());
        while (check(TokenType.COMMA)) {
            consume();
            args.add(parseExpression());
        }
        return args;
    }



    private Node parseExpression() throws ParseException {
        int line = current().line, col = current().column;
        Node left = parseSimpleExpr();
        Token t = current();
        switch (t.type) {
            case EQ: case NEQ: case LT: case GT: case LE: case GE:
                consume();
                Node right = parseSimpleExpr();
                return new Node.BinaryOpNode(t.value, left, right, line, col);
            default:
                return left;
        }
    }

    private Node parseSimpleExpr() throws ParseException {
        int line = current().line, col = current().column;

        String unary = null;
        if (check(TokenType.PLUS))  { unary = "+"; consume(); }
        else if (check(TokenType.MINUS)) { unary = "-"; consume(); }

        Node node = parseTerm();
        if (unary != null) {
            node = new Node.UnaryOpNode(unary, node, line, col);
        }

        while (true) {
            Token t = current();
            if (t.type == TokenType.PLUS || t.type == TokenType.MINUS
                    || t.type == TokenType.OR || t.type == TokenType.XOR) {
                consume();
                Node right = parseTerm();
                node = new Node.BinaryOpNode(t.value, node, right, t.line, t.column);
            } else break;
        }
        return node;
    }

    private Node parseTerm() throws ParseException {
        Node node = parseFactor();
        while (true) {
            Token t = current();
            if (t.type == TokenType.STAR || t.type == TokenType.SLASH
                    || t.type == TokenType.DIV || t.type == TokenType.MOD
                    || t.type == TokenType.AND) {
                consume();
                Node right = parseFactor();
                node = new Node.BinaryOpNode(t.value, node, right, t.line, t.column);
            } else break;
        }
        return node;
    }

    private Node parseFactor() throws ParseException {
        Token t = current();
        switch (t.type) {
            case INTEGER_LITERAL:
                consume();
                return new Node.IntLiteralNode(Integer.parseInt(t.value), t.line, t.column);
            case REAL_LITERAL:
                consume();
                return new Node.RealLiteralNode(Double.parseDouble(t.value), t.line, t.column);
            case CHAR_LITERAL:
                consume();
                return new Node.CharLiteralNode(t.value.charAt(0), t.line, t.column);
            case STRING_LITERAL:
                consume();
                return new Node.StringLiteralNode(t.value, t.line, t.column);
            case BOOLEAN_LITERAL:
                consume();
                return new Node.BoolLiteralNode(t.value.equals("true"), t.line, t.column);
            case NOT:
                consume();
                return new Node.UnaryOpNode("not", parseFactor(), t.line, t.column);
            case LPAREN:
                consume();
                Node expr = parseExpression();
                expect(TokenType.RPAREN);
                return expr;
            case INC: case DEC: case ABS: case LENGTH: case POS: case COPY:
                return parseSysCall();
            case IDENTIFIER: {
                int line = t.line, col = t.column;
                consume();

                if (check(TokenType.LPAREN)) {
                    consume();
                    List<Node> args = new ArrayList<>();
                    if (!check(TokenType.RPAREN)) args = parseArgList();
                    expect(TokenType.RPAREN);
                    return new Node.ProcCallNode(t.value, args, line, col);
                }

                if (check(TokenType.LBRACKET)) {
                    consume();
                    Node index = parseExpression();
                    expect(TokenType.RBRACKET);
                    return new Node.ArrayAccessNode(new Node.IdentifierNode(t.value, line, col), index, line, col);
                }
                return new Node.IdentifierNode(t.value, line, col);
            }
            default:
                throw new ParseException(
                    "Неожиданный токен '" + t.value + "' в выражении", t.line, t.column);
        }
    }
}
