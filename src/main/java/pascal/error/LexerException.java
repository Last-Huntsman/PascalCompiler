package pascal.error;

public class LexerException extends Exception {
    public final int line;
    public final int column;

    public LexerException(String message, int line, int column) {
        super(String.format("Лексическая ошибка [%d:%d]: %s", line, column, message));
        this.line = line;
        this.column = column;
    }
}
