package pascal.optimize;

import pascal.ast.Node;
import pascal.semantic.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AstOptimizer {
    public Node.ProgramNode optimize(Node.ProgramNode program) {
        List<Node> declarations = optimizeDeclarations(program.declarations);
        Node.BlockNode body = optimizeBlock(program.body);
        if (declarations == program.declarations && body == program.body) {
            return program;
        }

        Node.ProgramNode optimized = new Node.ProgramNode(program.name, declarations, body, program.line, program.column);
        return copySemantic(program, optimized);
    }

    private List<Node> optimizeDeclarations(List<Node> declarations) {
        List<Node> optimized = new ArrayList<>(declarations.size());
        boolean changed = false;
        for (Node declaration : declarations) {
            Node next = optimizeDeclaration(declaration);
            optimized.add(next);
            changed |= next != declaration;
        }
        return changed ? optimized : declarations;
    }

    private Node optimizeDeclaration(Node declaration) {
        if (declaration instanceof Node.VarSectionNode section) {
            List<Node.VarDeclNode> declarations = new ArrayList<>(section.declarations.size());
            boolean changed = false;
            for (Node.VarDeclNode varDecl : section.declarations) {
                Node typeNode = optimizeTypeNode(varDecl.typeNode);
                if (typeNode != varDecl.typeNode) {
                    Node.VarDeclNode rebuilt = copySemantic(varDecl,
                            new Node.VarDeclNode(varDecl.names, typeNode, varDecl.line, varDecl.column));
                    declarations.add(rebuilt);
                    changed = true;
                } else {
                    declarations.add(varDecl);
                }
            }
            if (!changed) {
                return declaration;
            }
            return copySemantic(section, new Node.VarSectionNode(declarations, section.line, section.column));
        }
        if (declaration instanceof Node.ConstSectionNode section) {
            List<Node.ConstDeclNode> declarations = new ArrayList<>(section.declarations.size());
            boolean changed = false;
            for (Node.ConstDeclNode constDecl : section.declarations) {
                Node value = optimizeExpression(constDecl.value);
                if (value != constDecl.value) {
                    Node.ConstDeclNode rebuilt = copySemantic(constDecl,
                            new Node.ConstDeclNode(constDecl.name, value, constDecl.line, constDecl.column));
                    declarations.add(rebuilt);
                    changed = true;
                } else {
                    declarations.add(constDecl);
                }
            }
            if (!changed) {
                return declaration;
            }
            return copySemantic(section, new Node.ConstSectionNode(declarations, section.line, section.column));
        }
        if (declaration instanceof Node.ProcedureNode procedure) {
            List<Node.ParamNode> params = optimizeParams(procedure.params);
            List<Node> declarations = optimizeDeclarations(procedure.declarations);
            Node.BlockNode body = optimizeBlock(procedure.body);
            if (params == procedure.params && declarations == procedure.declarations && body == procedure.body) {
                return declaration;
            }
            Node.ProcedureNode rebuilt = new Node.ProcedureNode(
                    procedure.name,
                    params,
                    declarations,
                    body,
                    procedure.line,
                    procedure.column
            );
            return copySemantic(procedure, rebuilt);
        }
        if (declaration instanceof Node.FunctionNode function) {
            List<Node.ParamNode> params = optimizeParams(function.params);
            Node returnType = optimizeTypeNode(function.returnType);
            List<Node> declarations = optimizeDeclarations(function.declarations);
            Node.BlockNode body = optimizeBlock(function.body);
            if (params == function.params
                    && returnType == function.returnType
                    && declarations == function.declarations
                    && body == function.body) {
                return declaration;
            }
            Node.FunctionNode rebuilt = new Node.FunctionNode(
                    function.name,
                    params,
                    returnType,
                    declarations,
                    body,
                    function.line,
                    function.column
            );
            return copySemantic(function, rebuilt);
        }
        return declaration;
    }

    private List<Node.ParamNode> optimizeParams(List<Node.ParamNode> params) {
        List<Node.ParamNode> optimized = new ArrayList<>(params.size());
        boolean changed = false;
        for (Node.ParamNode param : params) {
            Node typeNode = optimizeTypeNode(param.typeNode);
            if (typeNode != param.typeNode) {
                Node.ParamNode rebuilt = copySemantic(param,
                        new Node.ParamNode(param.names, typeNode, param.isVar, param.line, param.column));
                optimized.add(rebuilt);
                changed = true;
            } else {
                optimized.add(param);
            }
        }
        return changed ? optimized : params;
    }

    private Node optimizeTypeNode(Node typeNode) {
        if (typeNode instanceof Node.ArrayTypeNode array) {
            Node from = optimizeExpression(array.fromExpr);
            Node to = optimizeExpression(array.toExpr);
            Node elementType = optimizeTypeNode(array.elementType);
            if (from == array.fromExpr && to == array.toExpr && elementType == array.elementType) {
                return typeNode;
            }
            return copySemantic(array, new Node.ArrayTypeNode(from, to, elementType, array.line, array.column));
        }
        return typeNode;
    }

    private Node.BlockNode optimizeBlock(Node.BlockNode block) {
        List<Node> statements = new ArrayList<>(block.statements.size());
        boolean changed = false;
        for (Node statement : block.statements) {
            Node optimized = optimizeStatement(statement);
            if (optimized != statement) {
                changed = true;
            }
            if (optimized != null) {
                statements.add(optimized);
                if (isTerminal(optimized)) {
                    changed |= statements.size() != block.statements.size();
                    break;
                }
            } else {
                changed = true;
            }
        }

        if (!changed && statements.size() == block.statements.size()) {
            return block;
        }
        return copySemantic(block, new Node.BlockNode(statements, block.line, block.column));
    }

    private Node optimizeStatement(Node statement) {
        if (statement == null) {
            return null;
        }
        if (statement instanceof Node.BlockNode block) {
            return optimizeBlock(block);
        }
        if (statement instanceof Node.AssignNode assign) {
            Node target = optimizeExpression(assign.target);
            Node value = optimizeExpression(assign.value);
            if (target == assign.target && value == assign.value) {
                return statement;
            }
            return copySemantic(assign, new Node.AssignNode(target, value, assign.line, assign.column));
        }
        if (statement instanceof Node.IfNode ifNode) {
            Node condition = optimizeExpression(ifNode.condition);
            Node thenBranch = optimizeStatement(ifNode.thenBranch);
            Node elseBranch = optimizeStatement(ifNode.elseBranch);
            Boolean constant = asBoolean(condition);
            if (constant != null) {
                if (constant) {
                    return thenBranch == null ? emptyBlock(ifNode) : thenBranch;
                }
                return elseBranch == null ? emptyBlock(ifNode) : elseBranch;
            }
            if (condition == ifNode.condition && thenBranch == ifNode.thenBranch && elseBranch == ifNode.elseBranch) {
                return statement;
            }
            return copySemantic(ifNode, new Node.IfNode(condition, thenBranch, elseBranch, ifNode.line, ifNode.column));
        }
        if (statement instanceof Node.ForNode forNode) {
            Node from = optimizeExpression(forNode.fromExpr);
            Node to = optimizeExpression(forNode.toExpr);
            Node body = optimizeStatement(forNode.body);
            if (from == forNode.fromExpr && to == forNode.toExpr && body == forNode.body) {
                return statement;
            }
            return copySemantic(forNode,
                    new Node.ForNode(forNode.varName, from, to, forNode.downTo, body, forNode.line, forNode.column));
        }
        if (statement instanceof Node.WhileNode whileNode) {
            Node condition = optimizeExpression(whileNode.condition);
            Node body = optimizeStatement(whileNode.body);
            Boolean constant = asBoolean(condition);
            if (Boolean.FALSE.equals(constant)) {
                return emptyBlock(whileNode);
            }
            if (condition == whileNode.condition && body == whileNode.body) {
                return statement;
            }
            return copySemantic(whileNode, new Node.WhileNode(condition, body, whileNode.line, whileNode.column));
        }
        if (statement instanceof Node.RepeatNode repeatNode) {
            List<Node> statements = new ArrayList<>(repeatNode.statements.size());
            boolean changed = false;
            for (Node item : repeatNode.statements) {
                Node optimized = optimizeStatement(item);
                changed |= optimized != item;
                if (optimized != null) {
                    statements.add(optimized);
                    if (isTerminal(optimized)) {
                        changed |= statements.size() != repeatNode.statements.size();
                        break;
                    }
                } else {
                    changed = true;
                }
            }
            Node condition = optimizeExpression(repeatNode.condition);
            changed |= condition != repeatNode.condition;
            if (!changed && statements.size() == repeatNode.statements.size()) {
                return statement;
            }
            return copySemantic(repeatNode, new Node.RepeatNode(statements, condition, repeatNode.line, repeatNode.column));
        }
        if (statement instanceof Node.WriteNode writeNode) {
            List<Node> args = optimizeExpressions(writeNode.args);
            if (args == writeNode.args) {
                return statement;
            }
            return copySemantic(writeNode, new Node.WriteNode(writeNode.newLine, args, writeNode.line, writeNode.column));
        }
        if (statement instanceof Node.ReadNode readNode) {
            List<Node> args = optimizeExpressions(readNode.args);
            if (args == readNode.args) {
                return statement;
            }
            return copySemantic(readNode, new Node.ReadNode(readNode.newLine, args, readNode.line, readNode.column));
        }
        if (statement instanceof Node.ProcCallNode call) {
            List<Node> args = optimizeExpressions(call.args);
            if (args == call.args) {
                return statement;
            }
            return copySemantic(call, new Node.ProcCallNode(call.name, args, call.line, call.column));
        }
        if (statement instanceof Node.SysFuncCallNode call) {
            List<Node> args = optimizeExpressions(call.args);
            if (args == call.args) {
                return statement;
            }
            return copySemantic(call, new Node.SysFuncCallNode(call.funcName, args, call.line, call.column));
        }
        return statement;
    }

    private List<Node> optimizeExpressions(List<Node> args) {
        List<Node> optimized = new ArrayList<>(args.size());
        boolean changed = false;
        for (Node arg : args) {
            Node next = optimizeExpression(arg);
            optimized.add(next);
            changed |= next != arg;
        }
        return changed ? optimized : args;
    }

    private Node optimizeExpression(Node expression) {
        if (expression == null) {
            return null;
        }
        if (expression instanceof Node.ArrayAccessNode access) {
            Node array = optimizeExpression(access.array);
            Node index = optimizeExpression(access.index);
            if (array == access.array && index == access.index) {
                return expression;
            }
            return copySemantic(access, new Node.ArrayAccessNode(array, index, access.line, access.column));
        }
        if (expression instanceof Node.UnaryOpNode unary) {
            Node operand = optimizeExpression(unary.operand);
            if (unary.op.equalsIgnoreCase("not") && operand instanceof Node.UnaryOpNode nested
                    && nested.op.equalsIgnoreCase("not")) {
                return nested.operand;
            }
            Node folded = foldUnary(unary, operand);
            if (folded != null) {
                return folded;
            }
            if (operand == unary.operand) {
                return expression;
            }
            return copySemantic(unary, new Node.UnaryOpNode(unary.op, operand, unary.line, unary.column));
        }
        if (expression instanceof Node.BinaryOpNode binary) {
            Node left = optimizeExpression(binary.left);
            Node right = optimizeExpression(binary.right);
            Node simplified = simplifyBinary(binary, left, right);
            if (simplified != null) {
                return simplified;
            }
            Node folded = foldBinary(binary, left, right);
            if (folded != null) {
                return folded;
            }
            if (left == binary.left && right == binary.right) {
                return expression;
            }
            return copySemantic(binary, new Node.BinaryOpNode(binary.op, left, right, binary.line, binary.column));
        }
        if (expression instanceof Node.ProcCallNode call) {
            List<Node> args = optimizeExpressions(call.args);
            if (args == call.args) {
                return expression;
            }
            return copySemantic(call, new Node.ProcCallNode(call.name, args, call.line, call.column));
        }
        if (expression instanceof Node.SysFuncCallNode call) {
            List<Node> args = optimizeExpressions(call.args);
            if (args == call.args) {
                return expression;
            }
            return copySemantic(call, new Node.SysFuncCallNode(call.funcName, args, call.line, call.column));
        }
        return expression;
    }

    private Node simplifyBinary(Node.BinaryOpNode binary, Node left, Node right) {
        String op = binary.op.toLowerCase();
        if (op.equals("+")) {
            if (isZero(left)) {
                return right;
            }
            if (isZero(right)) {
                return left;
            }
        }
        if (op.equals("-") && isZero(right)) {
            return left;
        }
        if (op.equals("*")) {
            if (isOne(left)) {
                return right;
            }
            if (isOne(right)) {
                return left;
            }
            if (isZero(left) && isPure(right)) {
                return literalFromType(binary, 0);
            }
            if (isZero(right) && isPure(left)) {
                return literalFromType(binary, 0);
            }
        }
        if ((op.equals("/") || op.equals("div")) && isOne(right)) {
            return left;
        }
        if (op.equals("and")) {
            if (isBooleanLiteral(left, true)) {
                return right;
            }
            if (isBooleanLiteral(right, true)) {
                return left;
            }
        }
        if (op.equals("or")) {
            if (isBooleanLiteral(left, false)) {
                return right;
            }
            if (isBooleanLiteral(right, false)) {
                return left;
            }
        }
        if (op.equals("xor")) {
            if (isBooleanLiteral(left, false)) {
                return right;
            }
            if (isBooleanLiteral(right, false)) {
                return left;
            }
        }
        return null;
    }

    private Node foldUnary(Node.UnaryOpNode unary, Node operand) {
        if (operand instanceof Node.IntLiteralNode literal) {
            return switch (unary.op) {
                case "+" -> copySemantic(unary, literalNode(literal.value, Type.INTEGER, unary.line, unary.column));
                case "-" -> copySemantic(unary, literalNode(-literal.value, Type.INTEGER, unary.line, unary.column));
                default -> null;
            };
        }
        if (operand instanceof Node.RealLiteralNode literal) {
            return switch (unary.op) {
                case "+" -> copySemantic(unary, literalNode(literal.value, Type.DOUBLE, unary.line, unary.column));
                case "-" -> copySemantic(unary, literalNode(-literal.value, Type.DOUBLE, unary.line, unary.column));
                default -> null;
            };
        }
        if (operand instanceof Node.BoolLiteralNode literal && unary.op.equalsIgnoreCase("not")) {
            return copySemantic(unary, literalNode(!literal.value, Type.BOOLEAN, unary.line, unary.column));
        }
        return null;
    }

    private Node foldBinary(Node.BinaryOpNode binary, Node left, Node right) {
        Object leftValue = literalValue(left);
        Object rightValue = literalValue(right);
        if (leftValue == null || rightValue == null) {
            return null;
        }

        String op = binary.op.toLowerCase();
        try {
            return switch (op) {
                case "+", "-", "*", "/", "div", "mod" -> foldArithmetic(binary, leftValue, rightValue, op);
                case "and" -> copySemantic(binary, literalNode((Boolean) leftValue && (Boolean) rightValue, Type.BOOLEAN, binary.line, binary.column));
                case "or" -> copySemantic(binary, literalNode((Boolean) leftValue || (Boolean) rightValue, Type.BOOLEAN, binary.line, binary.column));
                case "xor" -> copySemantic(binary, literalNode((Boolean) leftValue ^ (Boolean) rightValue, Type.BOOLEAN, binary.line, binary.column));
                case "=", "<>", "<", "<=", ">", ">=" -> foldComparison(binary, leftValue, rightValue, op);
                default -> null;
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private Node foldArithmetic(Node.BinaryOpNode binary, Object leftValue, Object rightValue, String op) {
        if (op.equals("+") && binary.resolvedType == Type.STRING) {
            return copySemantic(binary, literalNode(String.valueOf(leftValue) + rightValue, Type.STRING, binary.line, binary.column));
        }
        if (op.equals("div")) {
            return copySemantic(binary, literalNode(asInt(leftValue) / asInt(rightValue), Type.INTEGER, binary.line, binary.column));
        }
        if (op.equals("mod")) {
            return copySemantic(binary, literalNode(asInt(leftValue) % asInt(rightValue), Type.INTEGER, binary.line, binary.column));
        }

        boolean useDouble = binary.resolvedType == Type.DOUBLE;
        if (useDouble) {
            double left = asDouble(leftValue);
            double right = asDouble(rightValue);
            return switch (op) {
                case "+" -> copySemantic(binary, literalNode(left + right, Type.DOUBLE, binary.line, binary.column));
                case "-" -> copySemantic(binary, literalNode(left - right, Type.DOUBLE, binary.line, binary.column));
                case "*" -> copySemantic(binary, literalNode(left * right, Type.DOUBLE, binary.line, binary.column));
                case "/" -> copySemantic(binary, literalNode(left / right, Type.DOUBLE, binary.line, binary.column));
                default -> null;
            };
        }

        int left = asInt(leftValue);
        int right = asInt(rightValue);
        return switch (op) {
            case "+" -> copySemantic(binary, literalNode(left + right, Type.INTEGER, binary.line, binary.column));
            case "-" -> copySemantic(binary, literalNode(left - right, Type.INTEGER, binary.line, binary.column));
            case "*" -> copySemantic(binary, literalNode(left * right, Type.INTEGER, binary.line, binary.column));
            case "/" -> copySemantic(binary, literalNode(left / right, Type.INTEGER, binary.line, binary.column));
            default -> null;
        };
    }

    private Node foldComparison(Node.BinaryOpNode binary, Object leftValue, Object rightValue, String op) {
        boolean result;
        if (leftValue instanceof String || rightValue instanceof String) {
            int comparison = String.valueOf(leftValue).compareTo(String.valueOf(rightValue));
            result = compare(comparison, op);
        } else if (leftValue instanceof Character || rightValue instanceof Character) {
            int comparison = Character.compare(asChar(leftValue), asChar(rightValue));
            result = compare(comparison, op);
        } else if (leftValue instanceof Boolean || rightValue instanceof Boolean) {
            int comparison = Boolean.compare((Boolean) leftValue, (Boolean) rightValue);
            result = compare(comparison, op);
        } else if (binary.left.resolvedType == Type.DOUBLE || binary.right.resolvedType == Type.DOUBLE) {
            int comparison = Double.compare(asDouble(leftValue), asDouble(rightValue));
            result = compare(comparison, op);
        } else {
            int comparison = Integer.compare(asInt(leftValue), asInt(rightValue));
            result = compare(comparison, op);
        }
        return copySemantic(binary, literalNode(result, Type.BOOLEAN, binary.line, binary.column));
    }

    private boolean compare(int comparison, String op) {
        return switch (op) {
            case "=" -> comparison == 0;
            case "<>" -> comparison != 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            default -> false;
        };
    }

    private boolean isTerminal(Node statement) {
        if (statement instanceof Node.ExitNode || statement instanceof Node.BreakNode || statement instanceof Node.ContinueNode) {
            return true;
        }
        if (statement instanceof Node.BlockNode block) {
            return !block.statements.isEmpty() && isTerminal(block.statements.get(block.statements.size() - 1));
        }
        if (statement instanceof Node.IfNode ifNode) {
            return ifNode.elseBranch != null && isTerminal(ifNode.thenBranch) && isTerminal(ifNode.elseBranch);
        }
        return false;
    }

    private boolean isPure(Node expression) {
        if (expression == null) {
            return true;
        }
        if (expression instanceof Node.IntLiteralNode
                || expression instanceof Node.RealLiteralNode
                || expression instanceof Node.BoolLiteralNode
                || expression instanceof Node.CharLiteralNode
                || expression instanceof Node.StringLiteralNode
                || expression instanceof Node.IdentifierNode) {
            return true;
        }
        if (expression instanceof Node.ArrayAccessNode access) {
            return isPure(access.array) && isPure(access.index);
        }
        if (expression instanceof Node.UnaryOpNode unary) {
            return isPure(unary.operand);
        }
        if (expression instanceof Node.BinaryOpNode binary) {
            return isPure(binary.left) && isPure(binary.right);
        }
        if (expression instanceof Node.SysFuncCallNode call) {
            String name = call.funcName.toLowerCase();
            if (name.equals("inc") || name.equals("dec")) {
                return false;
            }
            for (Node arg : call.args) {
                if (!isPure(arg)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private Boolean asBoolean(Node node) {
        if (node instanceof Node.BoolLiteralNode literal) {
            return literal.value;
        }
        return null;
    }

    private boolean isZero(Node node) {
        return (node instanceof Node.IntLiteralNode intLiteral && intLiteral.value == 0)
                || (node instanceof Node.RealLiteralNode realLiteral && realLiteral.value == 0.0);
    }

    private boolean isOne(Node node) {
        return (node instanceof Node.IntLiteralNode intLiteral && intLiteral.value == 1)
                || (node instanceof Node.RealLiteralNode realLiteral && realLiteral.value == 1.0);
    }

    private boolean isBooleanLiteral(Node node, boolean value) {
        return node instanceof Node.BoolLiteralNode literal && literal.value == value;
    }

    private Object literalValue(Node node) {
        if (node instanceof Node.IntLiteralNode literal) {
            return literal.value;
        }
        if (node instanceof Node.RealLiteralNode literal) {
            return literal.value;
        }
        if (node instanceof Node.BoolLiteralNode literal) {
            return literal.value;
        }
        if (node instanceof Node.CharLiteralNode literal) {
            return literal.value;
        }
        if (node instanceof Node.StringLiteralNode literal) {
            return literal.value;
        }
        return null;
    }

    private Node literalFromType(Node source, int intValue) {
        Type type = source.resolvedType;
        if (type == Type.DOUBLE) {
            return copySemantic(source, literalNode((double) intValue, Type.DOUBLE, source.line, source.column));
        }
        return copySemantic(source, literalNode(intValue, Type.INTEGER, source.line, source.column));
    }

    private Node literalNode(Object value, Type type, int line, int column) {
        Node node;
        if (type == Type.INTEGER) {
            node = new Node.IntLiteralNode(((Number) value).intValue(), line, column);
        } else if (type == Type.DOUBLE) {
            node = new Node.RealLiteralNode(((Number) value).doubleValue(), line, column);
        } else if (type == Type.BOOLEAN) {
            node = new Node.BoolLiteralNode((Boolean) value, line, column);
        } else if (type == Type.CHAR) {
            node = new Node.CharLiteralNode((Character) value, line, column);
        } else {
            node = new Node.StringLiteralNode(Objects.toString(value, ""), line, column);
        }
        node.resolvedType = type;
        return node;
    }

    private int asInt(Object value) {
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Double number) {
            return number.intValue();
        }
        throw new IllegalArgumentException("Expected integer-compatible literal");
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("Expected numeric literal");
    }

    private char asChar(Object value) {
        if (value instanceof Character ch) {
            return ch;
        }
        return Objects.toString(value, "").charAt(0);
    }

    private Node.BlockNode emptyBlock(Node source) {
        return copySemantic(source, new Node.BlockNode(List.of(), source.line, source.column));
    }

    private <T extends Node> T copySemantic(Node source, T target) {
        target.resolvedType = source.resolvedType;
        target.resolvedSymbol = source.resolvedSymbol;
        return target;
    }
}
