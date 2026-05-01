package pascal.ast;

import java.util.List;

/**
 * Базовый (абстрактный) класс для всех узлов AST.
 *
 * AST (Abstract Syntax Tree) — дерево, которое отражает структуру программы.
 * Каждый узел = одна конструкция языка (оператор, выражение, объявление).
 *
 * Например, для программы:
 *   if x > 0 then y := 1
 *
 * Дерево выглядит так:
 *   IfNode
 *   ├── BinaryOpNode (>)
 *   │   ├── IdentifierNode (x)
 *   │   └── IntLiteralNode (0)
 *   └── AssignNode (:=)
 *       ├── IdentifierNode (y)
 *       └── IntLiteralNode (1)
 */
public abstract class Node {

    // Позиция узла в исходном файле — нужна для сообщений об ошибках.
    // Например: "Ошибка [3:5]" означает строка 3, столбец 5.
    public final int line;
    public final int column;

    // Конструктор вызывается из каждого подкласса через super(line, col)
    protected Node(int line, int column) {
        this.line = line;
        this.column = column;
    }

    /**
     * Возвращает текстовое описание узла для печати дерева.
     * Каждый подкласс реализует своё — например:
     *   IfNode       → "If"
     *   BinaryOpNode → "BinaryOp: +"
     *   IntLitNode   → "IntLit: 42"
     */
    public abstract String nodeLabel();

    /**
     * Возвращает список дочерних узлов.
     * Используется при печати дерева и при обходе AST.
     * Листовые узлы (литералы, идентификаторы) возвращают пустой список.
     */


    public abstract List<Node> children();

    /**
     * Публичный метод — запускает печать дерева с корня.
     * Вызывается один раз снаружи: ast.printTree()
     */
    public void printTree() {
        printTree("", true);
    }

    /**
     * Рекурсивный метод печати дерева с псевдографикой.
     *
     * prefix  — строка отступа, накапливается при погружении в глубину
     * isLast  — является ли этот узел последним ребёнком своего родителя
     *
     * Пример вывода:
     *   └── If  [5:3]
     *       ├── BinaryOp: >  [5:6]
     *       │   ├── Identifier: x
     *       │   └── IntLit: 0
     *       └── Assign (:=)  [5:18]
     */
    private void printTree(String prefix, boolean isLast) {
        // Выбираем коннектор: "└── " для последнего ребёнка, "├── " для остальных
        String connector = isLast ? "└── " : "├── ";
        System.out.println(prefix + connector + nodeLabel() + posTag());

        // Отступ для детей: если мы последний — пробелы, если нет — вертикальная черта
        String childPrefix = prefix + (isLast ? "    " : "│   ");

        List<Node> kids = children();
        for (int i = 0; i < kids.size(); i++) {
            Node child = kids.get(i);
            if (child != null) {
                // Последний ребёнок в списке → isLast = true → коннектор "└──"
                child.printTree(childPrefix, i == kids.size() - 1);
            }
        }
    }

    /**
     * Формирует строку с позицией узла в сером цвете: [3:5]
     * \u001B[90m — ANSI-код серого цвета, \u001B[0m — сброс цвета.
     * Выводится после названия узла, чтобы не мешать читать.
     */
    private String posTag() {
        return String.format("  \u001B[90m[%d:%d]\u001B[0m", line, column);
    }


    // ── Корень программы ──────────────────────────────────────

    /**
     * Корневой узел — вся программа целиком.
     * Соответствует правилу грамматики:
     *   program Имя; Объявления begin ... end.
     */
    public static class ProgramNode extends Node {
        public final String name;              // имя программы: "program MyProg;"
        public final List<Node> declarations;  // секции var/const/procedure/function
        public final BlockNode body;           // тело: begin ... end

        public ProgramNode(String name, List<Node> declarations, BlockNode body, int line, int col) {
            super(line, col);
            this.name = name;
            this.declarations = declarations;
            this.body = body;
        }

        @Override public String nodeLabel() { return "Program: " + name; }

        // Дети: сначала все объявления, потом тело программы
        @Override public List<Node> children() {
            var list = new java.util.ArrayList<Node>(declarations);
            list.add(body);
            return list;
        }
    }


    // ── Объявления переменных ─────────────────────────────────

    /**
     * Секция объявления переменных: var x: integer; y: boolean;
     * Содержит список отдельных объявлений VarDeclNode.
     */
    public static class VarSectionNode extends Node {
        public final List<VarDeclNode> declarations;

        public VarSectionNode(List<VarDeclNode> declarations, int line, int col) {
            super(line, col);
            this.declarations = declarations;
        }

        @Override public String nodeLabel() { return "VarSection"; }
        @Override public List<Node> children() { return new java.util.ArrayList<>(declarations); }
    }

    /**
     * Одна строка объявления переменных: x, y, z : integer
     * names — список имён (может быть несколько через запятую)
     * typeNode — тип: SimpleTypeNode или ArrayTypeNode
     */
    public static class VarDeclNode extends Node {
        public final List<String> names;  // ["x", "y", "z"]
        public final Node typeNode;       // integer / array[1..10] of char / ...

        public VarDeclNode(List<String> names, Node typeNode, int line, int col) {
            super(line, col);
            this.names = names;
            this.typeNode = typeNode;
        }

        @Override public String nodeLabel() { return "VarDecl: " + String.join(", ", names); }

        // Единственный ребёнок — тип переменной
        @Override public List<Node> children() { return List.of(typeNode); }
    }

    /**
     * Секция констант: const N = 10; Pi = 3.14;
     */
    public static class ConstSectionNode extends Node {
        public final List<ConstDeclNode> declarations;

        public ConstSectionNode(List<ConstDeclNode> declarations, int line, int col) {
            super(line, col);
            this.declarations = declarations;
        }

        @Override public String nodeLabel() { return "ConstSection"; }
        @Override public List<Node> children() { return new java.util.ArrayList<>(declarations); }
    }

    /**
     * Одна константа: N = 10
     * value — любое константное выражение (литерал)
     */
    public static class ConstDeclNode extends Node {
        public final String name;   // имя константы
        public final Node value;    // значение: IntLiteralNode, StringLiteralNode и т.д.

        public ConstDeclNode(String name, Node value, int line, int col) {
            super(line, col);
            this.name = name;
            this.value = value;
        }

        @Override public String nodeLabel() { return "ConstDecl: " + name; }
        @Override public List<Node> children() { return List.of(value); }
    }


    // ── Типы данных ───────────────────────────────────────────

    /**
     * Простой тип: integer, char, boolean, string, double.
     * Также используется для пользовательских типов-псевдонимов.
     * Листовой узел — детей нет.
     */
    public static class SimpleTypeNode extends Node {
        public final String typeName; // "integer", "char", "boolean" ...

        public SimpleTypeNode(String typeName, int line, int col) {
            super(line, col);
            this.typeName = typeName;
        }

        @Override public String nodeLabel() { return "Type: " + typeName; }
        @Override public List<Node> children() { return List.of(); } // лист — нет детей
    }

    /**
     * Тип-массив: array[1..10] of integer
     *
     * fromExpr    — нижняя граница индекса (обычно IntLiteralNode)
     * toExpr      — верхняя граница индекса
     * elementType — тип элементов массива (SimpleTypeNode)
     */
    public static class ArrayTypeNode extends Node {
        public final Node fromExpr;    // нижняя граница: 1
        public final Node toExpr;      // верхняя граница: 10
        public final Node elementType; // тип элементов: integer

        public ArrayTypeNode(Node fromExpr, Node toExpr, Node elementType, int line, int col) {
            super(line, col);
            this.fromExpr = fromExpr;
            this.toExpr = toExpr;
            this.elementType = elementType;
        }

        @Override public String nodeLabel() { return "ArrayType"; }

        // Три ребёнка: от, до, тип элемента
        @Override public List<Node> children() { return List.of(fromExpr, toExpr, elementType); }
    }


    // ── Процедуры и функции ───────────────────────────────────

    /**
     * Объявление процедуры:
     *   procedure Swap(var a: integer; var b: integer);
     *   begin ... end;
     *
     * params       — список параметров (может быть пустым)
     * declarations — локальные var/const внутри процедуры
     * body         — тело: begin ... end
     */
    public static class ProcedureNode extends Node {
        public final String name;
        public final List<ParamNode> params;
        public final List<Node> declarations;
        public final BlockNode body;

        public ProcedureNode(String name, List<ParamNode> params,
                             List<Node> declarations, BlockNode body, int line, int col) {
            super(line, col);
            this.name = name;
            this.params = params;
            this.declarations = declarations;
            this.body = body;
        }

        @Override public String nodeLabel() { return "Procedure: " + name; }

        // Дети: параметры → локальные объявления → тело
        @Override public List<Node> children() {
            var list = new java.util.ArrayList<Node>(params);
            list.addAll(declarations);
            list.add(body);
            return list;
        }
    }

    /**
     * Объявление функции — то же что процедура, но есть тип возврата:
     *   function Factorial(n: integer): integer;
     *   begin ... end;
     *
     * returnType — тип возвращаемого значения (SimpleTypeNode)
     */
    public static class FunctionNode extends Node {
        public final String name;
        public final List<ParamNode> params;
        public final Node returnType;          // тип результата
        public final List<Node> declarations;
        public final BlockNode body;

        public FunctionNode(String name, List<ParamNode> params, Node returnType,
                            List<Node> declarations, BlockNode body, int line, int col) {
            super(line, col);
            this.name = name;
            this.params = params;
            this.returnType = returnType;
            this.declarations = declarations;
            this.body = body;
        }

        @Override public String nodeLabel() { return "Function: " + name; }

        // Дети: параметры → тип возврата → объявления → тело
        @Override public List<Node> children() {
            var list = new java.util.ArrayList<Node>(params);
            list.add(returnType);
            list.addAll(declarations);
            list.add(body);
            return list;
        }
    }

    /**
     * Один параметр функции/процедуры: x: integer  или  var x: integer
     *
     * isVar = false → передача по значению (копия)
     * isVar = true  → передача по ссылке (var x: integer), изменения видны снаружи
     */
    public static class ParamNode extends Node {
        public final List<String> names; // имена параметров: (a, b: integer)
        public final Node typeNode;      // тип параметра
        public final boolean isVar;      // передача по ссылке?

        public ParamNode(List<String> names, Node typeNode, boolean isVar, int line, int col) {
            super(line, col);
            this.names = names;
            this.typeNode = typeNode;
            this.isVar = isVar;
        }

        @Override public String nodeLabel() {
            // В метке показываем "(var)" если передача по ссылке
            return "Param" + (isVar ? "(var)" : "") + ": " + String.join(", ", names);
        }

        @Override public List<Node> children() { return List.of(typeNode); }
    }


    // ── Операторы ─────────────────────────────────────────────

    /**
     * Составной оператор: begin ... end
     * Содержит список операторов, выполняемых последовательно.
     * Используется как тело программы, процедуры, функции, циклов.
     */
    public static class BlockNode extends Node {
        public final List<Node> statements; // список операторов внутри begin...end

        public BlockNode(List<Node> statements, int line, int col) {
            super(line, col);
            this.statements = statements;
        }

        @Override public String nodeLabel() { return "Block"; }
        @Override public List<Node> children() { return new java.util.ArrayList<>(statements); }
    }

    /**
     * Оператор присваивания: x := выражение  или  a[i] := выражение
     *
     * target — левая часть (IdentifierNode или ArrayAccessNode)
     * value  — правая часть (любое выражение)
     */
    public static class AssignNode extends Node {
        public final Node target; // куда присваиваем
        public final Node value;  // что присваиваем

        public AssignNode(Node target, Node value, int line, int col) {
            super(line, col);
            this.target = target;
            this.value = value;
        }

        @Override public String nodeLabel() { return "Assign (:=)"; }

        // Два ребёнка: сначала цель, потом значение
        @Override public List<Node> children() { return List.of(target, value); }
    }

    /**
     * Условный оператор: if условие then оператор [else оператор]
     *
     * elseBranch может быть null — ветка else необязательна.
     * Это учитывается в children(): null-дети не добавляются.
     */
    public static class IfNode extends Node {
        public final Node condition;   // условие
        public final Node thenBranch;  // ветка then
        public final Node elseBranch;  // ветка else (или null если нет)

        public IfNode(Node condition, Node thenBranch, Node elseBranch, int line, int col) {
            super(line, col);
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override public String nodeLabel() { return "If"; }

        @Override public List<Node> children() {
            var list = new java.util.ArrayList<Node>();
            list.add(condition);
            list.add(thenBranch);
            if (elseBranch != null) list.add(elseBranch); // добавляем только если есть
            return list;
        }
    }

    /**
     * Цикл for: for i := 1 to 10 do тело
     *           for i := 10 downto 1 do тело
     *
     * varName  — имя счётчика цикла
     * fromExpr — начальное значение
     * toExpr   — конечное значение
     * downTo   — true если downto (убывающий), false если to (возрастающий)
     * body     — тело цикла
     */
    public static class ForNode extends Node {
        public final String varName;
        public final Node fromExpr;
        public final Node toExpr;
        public final boolean downTo; // направление: to или downto
        public final Node body;

        public ForNode(String varName, Node fromExpr, Node toExpr,
                       boolean downTo, Node body, int line, int col) {
            super(line, col);
            this.varName = varName;
            this.fromExpr = fromExpr;
            this.toExpr = toExpr;
            this.downTo = downTo;
            this.body = body;
        }

        @Override public String nodeLabel() {
            return "For: " + varName + (downTo ? " downto" : " to");
        }

        // Три ребёнка: от, до, тело
        @Override public List<Node> children() { return List.of(fromExpr, toExpr, body); }
    }

    /**
     * Цикл while: while условие do тело
     * Проверка условия — ДО выполнения тела.
     */
    public static class WhileNode extends Node {
        public final Node condition;
        public final Node body;

        public WhileNode(Node condition, Node body, int line, int col) {
            super(line, col);
            this.condition = condition;
            this.body = body;
        }

        @Override public String nodeLabel() { return "While"; }
        @Override public List<Node> children() { return List.of(condition, body); }
    }

    /**
     * Цикл repeat...until: repeat операторы until условие
     * Аналог do-while: тело выполняется ХОТЯ БЫ ОДИН РАЗ,
     * проверка условия — ПОСЛЕ выполнения тела.
     *
     * statements — список операторов внутри repeat...until
     * condition  — условие выхода (цикл продолжается пока condition = false)
     */
    public static class RepeatNode extends Node {
        public final List<Node> statements;
        public final Node condition;

        public RepeatNode(List<Node> statements, Node condition, int line, int col) {
            super(line, col);
            this.statements = statements;
            this.condition = condition;
        }

        @Override public String nodeLabel() { return "Repeat-Until"; }

        // Дети: сначала все операторы тела, потом условие
        @Override public List<Node> children() {
            var list = new java.util.ArrayList<Node>(statements);
            list.add(condition);
            return list;
        }
    }

    /**
     * Оператор break — выход из цикла.
     * Листовой узел, данных не несёт.
     */
    public static class BreakNode extends Node {
        public BreakNode(int line, int col) { super(line, col); }
        @Override public String nodeLabel() { return "Break"; }
        @Override public List<Node> children() { return List.of(); }
    }

    /**
     * Оператор continue — переход к следующей итерации цикла.
     * Листовой узел, данных не несёт.
     */
    public static class ContinueNode extends Node {
        public ContinueNode(int line, int col) { super(line, col); }
        @Override public String nodeLabel() { return "Continue"; }
        @Override public List<Node> children() { return List.of(); }
    }

    /**
     * Оператор exit — выход из процедуры/функции.
     * Листовой узел, данных не несёт.
     */
    public static class ExitNode extends Node {
        public ExitNode(int line, int col) { super(line, col); }
        @Override public String nodeLabel() { return "Exit"; }
        @Override public List<Node> children() { return List.of(); }
    }


    // ── Ввод / Вывод ──────────────────────────────────────────

    /**
     * Оператор вывода: write(...) или writeln(...)
     *
     * newLine = false → write   — без перевода строки
     * newLine = true  → writeln — с переводом строки
     * args — список выражений для вывода (может быть пустым у writeln)
     */
    public static class WriteNode extends Node {
        public final boolean newLine;
        public final List<Node> args;

        public WriteNode(boolean newLine, List<Node> args, int line, int col) {
            super(line, col);
            this.newLine = newLine;
            this.args = args;
        }

        @Override public String nodeLabel() { return newLine ? "WriteLn" : "Write"; }
        @Override public List<Node> children() { return new java.util.ArrayList<>(args); }
    }

    /**
     * Оператор ввода: read(...) или readln(...)
     *
     * newLine = false → read   — читает без перехода на новую строку
     * newLine = true  → readln — читает и переходит на новую строку
     * args — переменные, в которые читаем (должны быть IdentifierNode)
     */
    public static class ReadNode extends Node {
        public final boolean newLine;
        public final List<Node> args;

        public ReadNode(boolean newLine, List<Node> args, int line, int col) {
            super(line, col);
            this.newLine = newLine;
            this.args = args;
        }

        @Override public String nodeLabel() { return newLine ? "ReadLn" : "Read"; }
        @Override public List<Node> children() { return new java.util.ArrayList<>(args); }
    }


    // ── Выражения ─────────────────────────────────────────────

    /**
     * Бинарная операция: left OP right
     * Примеры: x + 1,  a > b,  x div 2,  flag and true
     *
     * op    — строка с оператором: "+", "-", "*", "/", "div", "mod",
     *         "=", "<>", "<", ">", "<=", ">=", "and", "or", "xor"
     * left  — левый операнд
     * right — правый операнд
     */
    public static class BinaryOpNode extends Node {
        public final String op;
        public final Node left;
        public final Node right;

        public BinaryOpNode(String op, Node left, Node right, int line, int col) {
            super(line, col);
            this.op = op;
            this.left = left;
            this.right = right;
        }

        @Override public String nodeLabel() { return "BinaryOp: " + op; }

        // Всегда ровно два ребёнка: левый и правый операнд
        @Override public List<Node> children() { return List.of(left, right); }
    }

    /**
     * Унарная операция: OP операнд
     * Примеры: -x,  not flag,  +5
     *
     * op      — оператор: "-", "+", "not"
     * operand — единственный операнд
     */
    public static class UnaryOpNode extends Node {
        public final String op;
        public final Node operand;

        public UnaryOpNode(String op, Node operand, int line, int col) {
            super(line, col);
            this.op = op;
            this.operand = operand;
        }

        @Override public String nodeLabel() { return "UnaryOp: " + op; }

        // Один ребёнок — операнд
        @Override public List<Node> children() { return List.of(operand); }
    }

    /**
     * Имя переменной/константы в выражении: x, myVar, PI
     * Листовой узел — только хранит имя, детей нет.
     */
    public static class IdentifierNode extends Node {
        public final String name;

        public IdentifierNode(String name, int line, int col) {
            super(line, col);
            this.name = name;
        }

        @Override public String nodeLabel() { return "Identifier: " + name; }
        @Override public List<Node> children() { return List.of(); } // лист
    }

    /**
     * Доступ к элементу массива: a[i]  или  matrix[row]
     *
     * array — узел самого массива (обычно IdentifierNode)
     * index — индекс (любое целочисленное выражение)
     */
    public static class ArrayAccessNode extends Node {
        public final Node array; // имя массива
        public final Node index; // индекс в скобках

        public ArrayAccessNode(Node array, Node index, int line, int col) {
            super(line, col);
            this.array = array;
            this.index = index;
        }

        @Override public String nodeLabel() { return "ArrayAccess"; }

        // Два ребёнка: массив и индекс
        @Override public List<Node> children() { return List.of(array, index); }
    }

    /**
     * Вызов процедуры или функции: Swap(a, b)  или  factorial(n)
     *
     * name — имя вызываемой подпрограммы
     * args — список аргументов (может быть пустым)
     *
     * Используется и как оператор (вызов процедуры),
     * и как выражение (вызов функции внутри выражения).
     */
    public static class ProcCallNode extends Node {
        public final String name;
        public final List<Node> args;

        public ProcCallNode(String name, List<Node> args, int line, int col) {
            super(line, col);
            this.name = name;
            this.args = args;
        }

        @Override public String nodeLabel() { return "Call: " + name; }

        // Дети — аргументы вызова
        @Override public List<Node> children() { return new java.util.ArrayList<>(args); }
    }

    /**
     * Вызов встроенной системной функции: Inc(x), Dec(x), Abs(n),
     *                                     Length(s), Pos(sub, s), Copy(s, from, len)
     *
     * Выделены в отдельный класс, чтобы отличать от пользовательских функций
     * на следующих фазах компилятора (семантический анализ, генерация кода).
     */
    public static class SysFuncCallNode extends Node {
        public final String funcName;
        public final List<Node> args;

        public SysFuncCallNode(String funcName, List<Node> args, int line, int col) {
            super(line, col);
            this.funcName = funcName;
            this.args = args;
        }

        @Override public String nodeLabel() { return "SysFunc: " + funcName; }
        @Override public List<Node> children() { return new java.util.ArrayList<>(args); }
    }


    // ── Литералы (константные значения прямо в коде) ──────────
    // Все литералы — листовые узлы: хранят значение, детей нет.

    /**
     * Целочисленный литерал: 0, 42, 1000
     */
    public static class IntLiteralNode extends Node {
        public final int value;

        public IntLiteralNode(int value, int line, int col) {
            super(line, col);
            this.value = value;
        }

        @Override public String nodeLabel() { return "IntLit: " + value; }
        @Override public List<Node> children() { return List.of(); }
    }

    /**
     * Вещественный литерал: 3.14, 0.001, 2.718
     */
    public static class RealLiteralNode extends Node {
        public final double value;

        public RealLiteralNode(double value, int line, int col) {
            super(line, col);
            this.value = value;
        }

        @Override public String nodeLabel() { return "RealLit: " + value; }
        @Override public List<Node> children() { return List.of(); }
    }

    /**
     * Символьный литерал: 'A', 'z', '5'
     * Отличается от строки тем, что ровно один символ.
     * Лексер сам определяет — строка или символ — по длине.
     */
    public static class CharLiteralNode extends Node {
        public final char value;

        public CharLiteralNode(char value, int line, int col) {
            super(line, col);
            this.value = value;
        }

        @Override public String nodeLabel() { return "CharLit: '" + value + "'"; }
        @Override public List<Node> children() { return List.of(); }
    }

    /**
     * Строковый литерал: 'hello', 'it''s fine'
     * Хранится уже с раскодированными кавычками ('' → ').
     */
    public static class StringLiteralNode extends Node {
        public final String value;

        public StringLiteralNode(String value, int line, int col) {
            super(line, col);
            this.value = value;
        }

        @Override public String nodeLabel() { return "StringLit: \"" + value + "\""; }
        @Override public List<Node> children() { return List.of(); }
    }

    /**
     * Булев литерал: true или false
     * В Паскале это ключевые слова, но по смыслу — константы.
     */
    public static class BoolLiteralNode extends Node {
        public final boolean value;

        public BoolLiteralNode(boolean value, int line, int col) {
            super(line, col);
            this.value = value;
        }

        @Override public String nodeLabel() { return "BoolLit: " + value; }
        @Override public List<Node> children() { return List.of(); }
    }
}