package pascal.semantic;

import pascal.ast.Node;

import java.util.ArrayList;
import java.util.List;

public class SemanticAnalyzer {
    private final List<String> errors = new ArrayList<>();
    private Scope globalScope;
    private int loopDepth;
    private Symbol currentRoutine;

    public AnalysisResult analyze(Node.ProgramNode program) {
        errors.clear();
        loopDepth = 0;
        currentRoutine = null;
        globalScope = new Scope(null);
        defineDeclarations(program.declarations, globalScope, false);
        analyzeRoutineBodies(program.declarations, globalScope);
        analyzeBlock(program.body, globalScope);
        return new AnalysisResult(globalScope, errors);
    }

    private void defineDeclarations(List<Node> declarations, Scope scope, boolean localRoutineDeclarationsUnsupported) {
        for (Node declaration : declarations) {
            if (declaration instanceof Node.VarSectionNode varSection) {
                defineVarSection(varSection, scope);
            } else if (declaration instanceof Node.ConstSectionNode constSection) {
                defineConstSection(constSection, scope);
            } else if (declaration instanceof Node.ProcedureNode procedure) {
                defineRoutine(procedure, scope, Type.VOID, localRoutineDeclarationsUnsupported);
            } else if (declaration instanceof Node.FunctionNode function) {
                Type returnType = resolveType(function.returnType);
                function.returnType.resolvedType = returnType;
                defineRoutine(function, scope, returnType, localRoutineDeclarationsUnsupported);
            }
        }
    }

    private void defineVarSection(Node.VarSectionNode section, Scope scope) {
        for (Node.VarDeclNode declaration : section.declarations) {
            Type type = resolveType(declaration.typeNode);
            declaration.typeNode.resolvedType = type;
            declaration.resolvedType = type;
            for (String name : declaration.names) {
                defineSymbol(scope, new Symbol(name, Symbol.Kind.VARIABLE, type), declaration);
            }
        }
    }

    private void defineConstSection(Node.ConstSectionNode section, Scope scope) {
        for (Node.ConstDeclNode declaration : section.declarations) {
            Type type = inferExpression(declaration.value, scope);
            declaration.resolvedType = type;
            defineSymbol(scope, new Symbol(declaration.name, Symbol.Kind.CONSTANT, type), declaration);
        }
    }

    private void defineRoutine(Node routine, Scope scope, Type returnType, boolean unsupportedNested) {
        if (unsupportedNested) {
            error(routine, "Nested procedures and functions are not supported by the code generator");
        }

        String name;
        List<Node.ParamNode> params;
        Symbol.Kind kind;
        if (routine instanceof Node.ProcedureNode procedure) {
            name = procedure.name;
            params = procedure.params;
            kind = Symbol.Kind.PROCEDURE;
        } else {
            Node.FunctionNode function = (Node.FunctionNode) routine;
            name = function.name;
            params = function.params;
            kind = Symbol.Kind.FUNCTION;
        }

        List<Symbol> parameterSymbols = new ArrayList<>();
        for (Node.ParamNode param : params) {
            Type type = resolveType(param.typeNode);
            param.typeNode.resolvedType = type;
            param.resolvedType = type;
            if (param.isVar) {
                error(param, "var parameters are not supported");
            }
            for (String paramName : param.names) {
                parameterSymbols.add(new Symbol(paramName, Symbol.Kind.PARAMETER, type, param.isVar));
            }
        }

        Symbol symbol = new Symbol(name, kind, returnType, false, parameterSymbols);
        routine.resolvedType = returnType;
        routine.resolvedSymbol = symbol;
        defineSymbol(scope, symbol, routine);
    }

    private void analyzeRoutineBodies(List<Node> declarations, Scope parentScope) {
        for (Node declaration : declarations) {
            if (declaration instanceof Node.ProcedureNode procedure) {
                analyzeProcedure(procedure, parentScope);
            } else if (declaration instanceof Node.FunctionNode function) {
                analyzeFunction(function, parentScope);
            }
        }
    }

    private void analyzeProcedure(Node.ProcedureNode procedure, Scope parentScope) {
        Symbol previousRoutine = currentRoutine;
        currentRoutine = procedure.resolvedSymbol;
        Scope routineScope = routineScope(procedure.resolvedSymbol, parentScope);
        defineDeclarations(procedure.declarations, routineScope, true);
        analyzeRoutineBodies(procedure.declarations, routineScope);
        analyzeBlock(procedure.body, routineScope);
        currentRoutine = previousRoutine;
    }

    private void analyzeFunction(Node.FunctionNode function, Scope parentScope) {
        Symbol previousRoutine = currentRoutine;
        currentRoutine = function.resolvedSymbol;
        Scope routineScope = routineScope(function.resolvedSymbol, parentScope);
        defineSymbol(routineScope, new Symbol(function.name, Symbol.Kind.VARIABLE, function.resolvedType), function);
        defineDeclarations(function.declarations, routineScope, true);
        analyzeRoutineBodies(function.declarations, routineScope);
        analyzeBlock(function.body, routineScope);
        currentRoutine = previousRoutine;
    }

    private Scope routineScope(Symbol routine, Scope parentScope) {
        Scope scope = new Scope(parentScope);
        for (Symbol parameter : routine.parameters()) {
            defineSymbol(scope, parameter, null);
        }
        return scope;
    }

    private Type resolveType(Node typeNode) {
        if (typeNode instanceof Node.SimpleTypeNode simple) {
            Type type = switch (simple.typeName.toLowerCase()) {
                case "integer" -> Type.INTEGER;
                case "char" -> Type.CHAR;
                case "boolean" -> Type.BOOLEAN;
                case "string" -> Type.STRING;
                case "double", "real" -> Type.DOUBLE;
                default -> {
                    error(typeNode, "Unknown type '" + simple.typeName + "'");
                    yield Type.ERROR;
                }
            };
            typeNode.resolvedType = type;
            return type;
        }

        if (typeNode instanceof Node.ArrayTypeNode array) {
            Type fromType = inferExpression(array.fromExpr, globalScope != null ? globalScope : new Scope(null));
            Type toType = inferExpression(array.toExpr, globalScope != null ? globalScope : new Scope(null));
            if (fromType != Type.INTEGER || toType != Type.INTEGER) {
                error(array, "Array bounds must be integer constants");
                typeNode.resolvedType = Type.ERROR;
                return Type.ERROR;
            }
            Integer lower = intLiteral(array.fromExpr);
            Integer upper = intLiteral(array.toExpr);
            if (lower == null || upper == null) {
                error(array, "Array bounds must be integer literals");
                typeNode.resolvedType = Type.ERROR;
                return Type.ERROR;
            }
            if (upper < lower) {
                error(array, "Array upper bound must be greater than or equal to lower bound");
                typeNode.resolvedType = Type.ERROR;
                return Type.ERROR;
            }
            Type elementType = resolveType(array.elementType);
            Type type = Type.array(elementType, lower, upper);
            typeNode.resolvedType = type;
            return type;
        }

        error(typeNode, "Unsupported type declaration");
        return Type.ERROR;
    }

    private Integer intLiteral(Node node) {
        if (node instanceof Node.IntLiteralNode literal) return literal.value;
        return null;
    }

    private void analyzeBlock(Node.BlockNode block, Scope scope) {
        for (Node statement : block.statements) {
            analyzeStatement(statement, scope);
        }
    }

    private void analyzeStatement(Node statement, Scope scope) {
        if (statement == null) return;
        if (statement instanceof Node.BlockNode block) {
            analyzeBlock(block, scope);
        } else if (statement instanceof Node.AssignNode assign) {
            analyzeAssign(assign, scope);
        } else if (statement instanceof Node.IfNode ifNode) {
            requireBoolean(ifNode.condition, inferExpression(ifNode.condition, scope));
            analyzeStatement(ifNode.thenBranch, scope);
            analyzeStatement(ifNode.elseBranch, scope);
        } else if (statement instanceof Node.ForNode forNode) {
            Symbol counter = scope.resolve(forNode.varName);
            if (counter == null) {
                error(forNode, "Undeclared for-loop variable '" + forNode.varName + "'");
            } else if (!counter.isWritable() || counter.type() != Type.INTEGER) {
                error(forNode, "For-loop variable must be a writable integer");
            }
            requireInteger(forNode.fromExpr, inferExpression(forNode.fromExpr, scope));
            requireInteger(forNode.toExpr, inferExpression(forNode.toExpr, scope));
            loopDepth++;
            analyzeStatement(forNode.body, scope);
            loopDepth--;
        } else if (statement instanceof Node.WhileNode whileNode) {
            requireBoolean(whileNode.condition, inferExpression(whileNode.condition, scope));
            loopDepth++;
            analyzeStatement(whileNode.body, scope);
            loopDepth--;
        } else if (statement instanceof Node.RepeatNode repeatNode) {
            loopDepth++;
            for (Node item : repeatNode.statements) analyzeStatement(item, scope);
            loopDepth--;
            requireBoolean(repeatNode.condition, inferExpression(repeatNode.condition, scope));
        } else if (statement instanceof Node.BreakNode || statement instanceof Node.ContinueNode) {
            if (loopDepth == 0) error(statement, statement.nodeLabel() + " is allowed only inside a loop");
        } else if (statement instanceof Node.WriteNode writeNode) {
            for (Node arg : writeNode.args) inferExpression(arg, scope);
        } else if (statement instanceof Node.ReadNode readNode) {
            analyzeRead(readNode, scope);
        } else if (statement instanceof Node.SysFuncCallNode sysFunc) {
            inferSysFunc(sysFunc, scope, true);
        } else if (statement instanceof Node.ProcCallNode call) {
            Type type = inferCall(call, scope);
            if (type != Type.VOID && type != Type.ERROR) {
                error(call, "Function call result is ignored");
            }
        } else if (statement instanceof Node.ExitNode) {
            if (currentRoutine == null) error(statement, "Exit is allowed only inside procedure or function");
        }
    }

    private void analyzeAssign(Node.AssignNode assign, Scope scope) {
        Type targetType = inferLValue(assign.target, scope);
        Type valueType = inferExpression(assign.value, scope);
        if (!targetType.canAssignFrom(valueType)) {
            error(assign, "Cannot assign " + valueType.displayName() + " to " + targetType.displayName());
        }
        assign.resolvedType = targetType;
    }

    private void analyzeRead(Node.ReadNode readNode, Scope scope) {
        for (Node arg : readNode.args) {
            Type type = inferLValue(arg, scope);
            if (type.kind() == Type.Kind.ARRAY || type == Type.VOID || type == Type.ERROR) {
                error(arg, "Read argument must be a scalar writable variable");
            }
        }
    }

    private Type inferLValue(Node target, Scope scope) {
        Type type = inferExpression(target, scope);
        if (target instanceof Node.IdentifierNode identifier) {
            Symbol symbol = scope.resolve(identifier.name);
            if (symbol != null && !symbol.isWritable()) {
                error(target, "Target '" + identifier.name + "' is not writable");
            }
        } else if (target instanceof Node.ArrayAccessNode access) {
            Type ownerType = access.array.resolvedType;
            if (ownerType == Type.STRING) {
                error(access, "String characters are read-only");
            }
        } else {
            error(target, "Assignment target must be a variable or array element");
        }
        return type;
    }

    private Type inferExpression(Node expression, Scope scope) {
        if (expression == null) return Type.ERROR;

        Type type;
        if (expression instanceof Node.IntLiteralNode) {
            type = Type.INTEGER;
        } else if (expression instanceof Node.RealLiteralNode) {
            type = Type.DOUBLE;
        } else if (expression instanceof Node.BoolLiteralNode) {
            type = Type.BOOLEAN;
        } else if (expression instanceof Node.CharLiteralNode) {
            type = Type.CHAR;
        } else if (expression instanceof Node.StringLiteralNode) {
            type = Type.STRING;
        } else if (expression instanceof Node.IdentifierNode identifier) {
            type = inferIdentifier(identifier, scope);
        } else if (expression instanceof Node.ArrayAccessNode access) {
            type = inferArrayAccess(access, scope);
        } else if (expression instanceof Node.BinaryOpNode binary) {
            type = inferBinary(binary, scope);
        } else if (expression instanceof Node.UnaryOpNode unary) {
            type = inferUnary(unary, scope);
        } else if (expression instanceof Node.ProcCallNode call) {
            type = inferCall(call, scope);
        } else if (expression instanceof Node.SysFuncCallNode sysFunc) {
            type = inferSysFunc(sysFunc, scope, false);
        } else {
            error(expression, "Unsupported expression");
            type = Type.ERROR;
        }

        expression.resolvedType = type;
        return type;
    }

    private Type inferIdentifier(Node.IdentifierNode identifier, Scope scope) {
        Symbol symbol = scope.resolve(identifier.name);
        if (symbol == null) {
            error(identifier, "Undeclared identifier '" + identifier.name + "'");
            return Type.ERROR;
        }
        identifier.resolvedSymbol = symbol;
        if (symbol.kind() == Symbol.Kind.PROCEDURE) {
            error(identifier, "Procedure '" + identifier.name + "' has no value");
            return Type.ERROR;
        }
        return symbol.type();
    }

    private Type inferArrayAccess(Node.ArrayAccessNode access, Scope scope) {
        Type arrayType = inferExpression(access.array, scope);
        Type indexType = inferExpression(access.index, scope);
        requireInteger(access.index, indexType);
        if (arrayType.kind() == Type.Kind.ARRAY) return arrayType.elementType();
        if (arrayType == Type.STRING) return Type.CHAR;
        error(access, "Indexed access requires array or string, got " + arrayType.displayName());
        return Type.ERROR;
    }

    private Type inferUnary(Node.UnaryOpNode unary, Scope scope) {
        Type operand = inferExpression(unary.operand, scope);
        return switch (unary.op.toLowerCase()) {
            case "+" -> {
                if (!operand.isNumeric()) error(unary, "Unary + requires numeric operand");
                yield operand.isNumeric() ? operand : Type.ERROR;
            }
            case "-" -> {
                if (!operand.isNumeric()) error(unary, "Unary - requires numeric operand");
                yield operand.isNumeric() ? operand : Type.ERROR;
            }
            case "not" -> {
                requireBoolean(unary.operand, operand);
                yield Type.BOOLEAN;
            }
            default -> {
                error(unary, "Unknown unary operator '" + unary.op + "'");
                yield Type.ERROR;
            }
        };
    }

    private Type inferBinary(Node.BinaryOpNode binary, Scope scope) {
        Type left = inferExpression(binary.left, scope);
        Type right = inferExpression(binary.right, scope);
        String op = binary.op.toLowerCase();

        return switch (op) {
            case "+", "-", "*", "/", "div", "mod" -> arithmeticType(binary, left, right, op);
            case "and", "or", "xor" -> {
                requireBoolean(binary.left, left);
                requireBoolean(binary.right, right);
                yield Type.BOOLEAN;
            }
            case "=", "<>", "<", ">", "<=", ">=" -> comparisonType(binary, left, right, op);
            default -> {
                error(binary, "Unknown binary operator '" + binary.op + "'");
                yield Type.ERROR;
            }
        };
    }

    private Type arithmeticType(Node node, Type left, Type right, String op) {
        if (op.equals("+") && (left == Type.STRING || right == Type.STRING)) return Type.STRING;
        if (op.equals("+") && left == Type.CHAR && right == Type.CHAR) return Type.STRING;
        if (!left.isNumeric() || !right.isNumeric()) {
            error(node, "Operator '" + op + "' requires numeric operands");
            return Type.ERROR;
        }
        if (op.equals("div") || op.equals("mod")) {
            requireInteger(node, left);
            requireInteger(node, right);
            return Type.INTEGER;
        }
        if (op.equals("/")) {
            return left == Type.DOUBLE || right == Type.DOUBLE ? Type.DOUBLE : Type.INTEGER;
        }
        return left == Type.DOUBLE || right == Type.DOUBLE ? Type.DOUBLE : Type.INTEGER;
    }

    private Type comparisonType(Node node, Type left, Type right, String op) {
        if (left == Type.ERROR || right == Type.ERROR) return Type.BOOLEAN;
        if (left.equals(right)) {
            if ((left == Type.STRING || left == Type.BOOLEAN) && !op.equals("=") && !op.equals("<>")) {
                error(node, "Only = and <> comparisons are supported for " + left.displayName());
            }
            return Type.BOOLEAN;
        }
        if (left.isNumeric() && right.isNumeric()) return Type.BOOLEAN;
        error(node, "Cannot compare " + left.displayName() + " and " + right.displayName());
        return Type.BOOLEAN;
    }

    private Type inferCall(Node.ProcCallNode call, Scope scope) {
        Symbol symbol = scope.resolve(call.name);
        if (symbol == null) {
            error(call, "Undeclared routine '" + call.name + "'");
            return Type.ERROR;
        }
        call.resolvedSymbol = symbol;
        if (!symbol.isRoutine()) {
            error(call, "'" + call.name + "' is not a procedure or function");
            return Type.ERROR;
        }
        checkArguments(call, symbol, scope);
        return symbol.type();
    }

    private Type inferSysFunc(Node.SysFuncCallNode call, Scope scope, boolean asStatement) {
        String name = call.funcName.toLowerCase();
        return switch (name) {
            case "inc", "dec" -> {
                requireArgCount(call, 1);
                if (!call.args.isEmpty()) requireInteger(call.args.get(0), inferLValue(call.args.get(0), scope));
                yield Type.VOID;
            }
            case "abs" -> {
                requireArgCount(call, 1);
                Type arg = call.args.isEmpty() ? Type.ERROR : inferExpression(call.args.get(0), scope);
                if (!arg.isNumeric()) error(call, "Abs requires integer or double argument");
                if (asStatement) error(call, "Abs result cannot be used as a statement");
                yield arg.isNumeric() ? arg : Type.ERROR;
            }
            case "length" -> {
                requireArgCount(call, 1);
                Type arg = call.args.isEmpty() ? Type.ERROR : inferExpression(call.args.get(0), scope);
                if (arg != Type.STRING && arg.kind() != Type.Kind.ARRAY) error(call, "Length requires string or array argument");
                if (asStatement) error(call, "Length result cannot be used as a statement");
                yield Type.INTEGER;
            }
            case "pos" -> {
                requireArgCount(call, 2);
                Type first = call.args.size() > 0 ? inferExpression(call.args.get(0), scope) : Type.ERROR;
                Type second = call.args.size() > 1 ? inferExpression(call.args.get(1), scope) : Type.ERROR;
                if ((first != Type.STRING && first != Type.CHAR) || second != Type.STRING) {
                    error(call, "Pos requires (string|char, string)");
                }
                if (asStatement) error(call, "Pos result cannot be used as a statement");
                yield Type.INTEGER;
            }
            case "copy" -> {
                requireArgCount(call, 3);
                Type source = call.args.size() > 0 ? inferExpression(call.args.get(0), scope) : Type.ERROR;
                Type start = call.args.size() > 1 ? inferExpression(call.args.get(1), scope) : Type.ERROR;
                Type count = call.args.size() > 2 ? inferExpression(call.args.get(2), scope) : Type.ERROR;
                if (source != Type.STRING || start != Type.INTEGER || count != Type.INTEGER) {
                    error(call, "Copy requires (string, integer, integer)");
                }
                if (asStatement) error(call, "Copy result cannot be used as a statement");
                yield Type.STRING;
            }
            default -> {
                error(call, "Unknown system function '" + call.funcName + "'");
                yield Type.ERROR;
            }
        };
    }

    private void checkArguments(Node.ProcCallNode call, Symbol routine, Scope scope) {
        List<Symbol> params = routine.parameters();
        if (params.size() != call.args.size()) {
            error(call, "Routine '" + routine.name() + "' expects " + params.size()
                    + " arguments, got " + call.args.size());
        }
        int count = Math.min(params.size(), call.args.size());
        for (int i = 0; i < count; i++) {
            Symbol param = params.get(i);
            Type argType = inferExpression(call.args.get(i), scope);
            if (!param.type().canAssignFrom(argType)) {
                error(call.args.get(i), "Argument " + (i + 1) + " must be "
                        + param.type().displayName() + ", got " + argType.displayName());
            }
            if (param.byReference()) {
                inferLValue(call.args.get(i), scope);
            }
        }
    }

    private void requireArgCount(Node.SysFuncCallNode call, int expected) {
        if (call.args.size() != expected) {
            error(call, call.funcName + " expects " + expected + " arguments, got " + call.args.size());
        }
    }

    private void requireInteger(Node node, Type type) {
        if (type != Type.INTEGER && type != Type.ERROR) {
            error(node, "Integer expression expected, got " + type.displayName());
        }
    }

    private void requireBoolean(Node node, Type type) {
        if (type != Type.BOOLEAN && type != Type.ERROR) {
            error(node, "Boolean expression expected, got " + type.displayName());
        }
    }

    private void defineSymbol(Scope scope, Symbol symbol, Node node) {
        if (!scope.define(symbol) && node != null) {
            error(node, "Duplicate declaration of '" + symbol.name() + "'");
        }
    }

    private void error(Node node, String message) {
        if (node == null) {
            errors.add("Semantic error: " + message);
            return;
        }
        errors.add(String.format("Semantic error [%d:%d]: %s", node.line, node.column, message));
    }
}
