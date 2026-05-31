package pascal.lexer;

import pascal.error.LexerException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private int pos;
    private int line;
    private int column;
    private final List<Token> tokens;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        KEYWORDS.put("program",   TokenType.PROGRAM);
        KEYWORDS.put("var",       TokenType.VAR);
        KEYWORDS.put("begin",     TokenType.BEGIN);
        KEYWORDS.put("end",       TokenType.END);
        KEYWORDS.put("const",     TokenType.CONST);
        KEYWORDS.put("if",        TokenType.IF);
        KEYWORDS.put("then",      TokenType.THEN);
        KEYWORDS.put("else",      TokenType.ELSE);
        KEYWORDS.put("for",       TokenType.FOR);
        KEYWORDS.put("to",        TokenType.TO);
        KEYWORDS.put("downto",    TokenType.DOWNTO);
        KEYWORDS.put("do",        TokenType.DO);
        KEYWORDS.put("while",     TokenType.WHILE);
        KEYWORDS.put("repeat",    TokenType.REPEAT);
        KEYWORDS.put("until",     TokenType.UNTIL);
        KEYWORDS.put("procedure", TokenType.PROCEDURE);
        KEYWORDS.put("function",  TokenType.FUNCTION);
        KEYWORDS.put("array",     TokenType.ARRAY);
        KEYWORDS.put("of",        TokenType.OF);
        KEYWORDS.put("type",      TokenType.TYPE);
        KEYWORDS.put("record",    TokenType.RECORD);
        KEYWORDS.put("break",     TokenType.BREAK);
        KEYWORDS.put("continue",  TokenType.CONTINUE);
        KEYWORDS.put("exit",      TokenType.EXIT);
        KEYWORDS.put("integer",   TokenType.INTEGER);
        KEYWORDS.put("char",      TokenType.CHAR);
        KEYWORDS.put("boolean",   TokenType.BOOLEAN);
        KEYWORDS.put("string",    TokenType.STRING);
        KEYWORDS.put("double",    TokenType.DOUBLE);
        KEYWORDS.put("real",      TokenType.DOUBLE);
        KEYWORDS.put("div",       TokenType.DIV);
        KEYWORDS.put("mod",       TokenType.MOD);
        KEYWORDS.put("and",       TokenType.AND);
        KEYWORDS.put("or",        TokenType.OR);
        KEYWORDS.put("not",       TokenType.NOT);
        KEYWORDS.put("xor",       TokenType.XOR);
        KEYWORDS.put("true",      TokenType.BOOLEAN_LITERAL);
        KEYWORDS.put("false",     TokenType.BOOLEAN_LITERAL);
        KEYWORDS.put("write",     TokenType.WRITE);
        KEYWORDS.put("writeln",   TokenType.WRITELN);
        KEYWORDS.put("read",      TokenType.READ);
        KEYWORDS.put("readln",    TokenType.READLN);
        KEYWORDS.put("inc",       TokenType.INC);
        KEYWORDS.put("dec",       TokenType.DEC);
        KEYWORDS.put("abs",       TokenType.ABS);
        KEYWORDS.put("length",    TokenType.LENGTH);
        KEYWORDS.put("pos",       TokenType.POS);
        KEYWORDS.put("copy",      TokenType.COPY);
    }

    public Lexer(String source) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
        this.tokens = new ArrayList<>();
    }

    private char current() {
        if (pos >= source.length()) return '\0';
        return source.charAt(pos);
    }

    private char peek(int offset) {
        int idx = pos + offset;
        if (idx >= source.length()) return '\0';
        return source.charAt(idx);
    }

    private char advance() {
        char c = current();
        pos++;
        if (c == '\n') { line++; column = 1; }
        else { column++; }
        return c;
    }

    private void skipWhitespace() {
        while (pos < source.length() && (Character.isWhitespace(current()) || current() == '\uFEFF')) {
            advance();
        }
    }


    private void skipComment() {
        if (current() == '{') {
            advance(); // skip '{'
            while (pos < source.length() && current() != '}') {
                advance();
            }
            if (current() == '}') advance();
        } else if (current() == '(' && peek(1) == '*') {
            advance(); advance(); // skip (*
            while (pos < source.length() && !(current() == '*' && peek(1) == ')')) {
                advance();
            }
            if (pos < source.length()) { advance(); advance(); } // skip *)
        } else if (current() == '/' && peek(1) == '/') {
            while (pos < source.length() && current() != '\n') advance();
        }
    }

    private Token readNumber(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && Character.isDigit(current())) {
            sb.append(advance());
        }
        if (current() == '.' && peek(1) != '.') {
            sb.append(advance());
            while (pos < source.length() && Character.isDigit(current())) {
                sb.append(advance());
            }
            return new Token(TokenType.REAL_LITERAL, sb.toString(), startLine, startCol);
        }
        return new Token(TokenType.INTEGER_LITERAL, sb.toString(), startLine, startCol);
    }

    private Token readString(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        advance();
        while (pos < source.length()) {
            if (current() == '\'' && peek(1) == '\'') {
                sb.append('\'');
                advance(); advance();
            } else if (current() == '\'') {
                break;
            } else {
                sb.append(advance());
            }
        }
        if (current() == '\'') advance();
        String val = sb.toString();
        if (val.length() == 1) {
            return new Token(TokenType.CHAR_LITERAL, val, startLine, startCol);
        }
        return new Token(TokenType.STRING_LITERAL, val, startLine, startCol);
    }

    private Token readIdentifierOrKeyword(int startLine, int startCol) {
        StringBuilder sb = new StringBuilder();
        while (pos < source.length() && (Character.isLetterOrDigit(current()) || current() == '_')) {
            sb.append(advance());
        }
        String word = sb.toString().toLowerCase();
        TokenType type = KEYWORDS.getOrDefault(word, TokenType.IDENTIFIER);

        String tokenValue = (type == TokenType.IDENTIFIER) ? sb.toString() : word;
        return new Token(type, tokenValue, startLine, startCol);
    }

    public List<Token> tokenize() throws LexerException {
        while (pos < source.length()) {
            skipWhitespace();
            if (pos >= source.length()) break;

            int startLine = line;
            int startCol = column;
            char c = current();


            if (c == '{' || (c == '(' && peek(1) == '*') || (c == '/' && peek(1) == '/')) {
                skipComment();
                continue;
            }


            if (Character.isDigit(c)) {
                tokens.add(readNumber(startLine, startCol));
                continue;
            }


            if (c == '\'') {
                tokens.add(readString(startLine, startCol));
                continue;
            }


            if (Character.isLetter(c) || c == '_') {
                tokens.add(readIdentifierOrKeyword(startLine, startCol));
                continue;
            }


            advance();
            switch (c) {
                case '+': tokens.add(new Token(TokenType.PLUS,      "+", startLine, startCol)); break;
                case '-': tokens.add(new Token(TokenType.MINUS,     "-", startLine, startCol)); break;
                case '*': tokens.add(new Token(TokenType.STAR,      "*", startLine, startCol)); break;
                case '/': tokens.add(new Token(TokenType.SLASH,     "/", startLine, startCol)); break;
                case '(': tokens.add(new Token(TokenType.LPAREN,    "(", startLine, startCol)); break;
                case ')': tokens.add(new Token(TokenType.RPAREN,    ")", startLine, startCol)); break;
                case '[': tokens.add(new Token(TokenType.LBRACKET,  "[", startLine, startCol)); break;
                case ']': tokens.add(new Token(TokenType.RBRACKET,  "]", startLine, startCol)); break;
                case ',': tokens.add(new Token(TokenType.COMMA,     ",", startLine, startCol)); break;
                case ';': tokens.add(new Token(TokenType.SEMICOLON, ";", startLine, startCol)); break;
                case '=': tokens.add(new Token(TokenType.EQ,        "=", startLine, startCol)); break;
                case ':':
                    if (current() == '=') {
                        advance();
                        tokens.add(new Token(TokenType.ASSIGN, ":=", startLine, startCol));
                    } else {
                        tokens.add(new Token(TokenType.COLON, ":", startLine, startCol));
                    }
                    break;
                case '.':
                    if (current() == '.') {
                        advance();
                        tokens.add(new Token(TokenType.DOTDOT, "..", startLine, startCol));
                    } else {
                        tokens.add(new Token(TokenType.DOT, ".", startLine, startCol));
                    }
                    break;
                case '<':
                    if (current() == '=') { advance(); tokens.add(new Token(TokenType.LE,  "<=", startLine, startCol)); }
                    else if (current() == '>') { advance(); tokens.add(new Token(TokenType.NEQ, "<>", startLine, startCol)); }
                    else tokens.add(new Token(TokenType.LT, "<", startLine, startCol));
                    break;
                case '>':
                    if (current() == '=') { advance(); tokens.add(new Token(TokenType.GE, ">=", startLine, startCol)); }
                    else tokens.add(new Token(TokenType.GT, ">", startLine, startCol));
                    break;
                default:
                    throw new LexerException("Неизвестный символ: '" + c + "'", startLine, startCol);
            }
        }
        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }
}
