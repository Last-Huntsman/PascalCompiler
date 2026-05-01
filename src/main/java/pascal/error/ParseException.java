package pascal.error;

public class ParseException extends Exception {
    public final int line;
    public final int column;

    public ParseException(String message, int line, int column) {
        super(String.format("Синтаксическая ошибка [%d:%d]: %s", line, column, message));
        this.line = line;
        this.column = column;
    }
}
