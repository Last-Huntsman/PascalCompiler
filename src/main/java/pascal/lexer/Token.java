package pascal.lexer;

public class Token {
    public final TokenType type;
    public final String value;
    public final int line;
    public final int column;

    public Token(TokenType type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return String.format("Token(%s, '%s', %d:%d)", type, value, line, column);
    }
}
