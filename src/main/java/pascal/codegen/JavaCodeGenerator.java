package pascal.codegen;

import pascal.ast.Node;
import pascal.semantic.Symbol;
import pascal.semantic.Type;

import java.util.ArrayList;
import java.util.List;

public class JavaCodeGenerator {
    private final StringBuilder out = new StringBuilder();
    private int indent;
    private String currentFunctionName;

    public String generate(Node.ProgramNode program) {
        out.setLength(0);
        indent = 0;
        currentFunctionName = null;

        String className = className(program.name);
        line("public class " + className + " {");
        indent++;
        line("private static final java.util.Scanner __scanner = new java.util.Scanner(System.in);");
        line("");
        emitGlobalDeclarations(program.declarations);
        emitRoutines(program.declarations);
        line("public static void main(String[] args) {");
        indent++;
        emitBlockStatements(program.body);
        indent--;
        line("}");
        emitHelpers();
        indent--;
        line("}");
        return out.toString();
    }

    public String className(String programName) {
        String base = javaIdentifier(programName);
        if (base.isBlank()) return "GeneratedPascalProgram";
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    private void emitGlobalDeclarations(List<Node> declarations) {
        for (Node declaration : declarations) {
            if (declaration instanceof Node.VarSectionNode section) {
                for (Node.VarDeclNode varDecl : section.declarations) {
                    Type type = varDecl.typeNode.resolvedType;
                    for (String name : varDecl.names) {
                        line("static " + javaType(type) + " " + javaIdentifier(name) + " = " + defaultValue(type) + ";");
                    }
                }
            } else if (declaration instanceof Node.ConstSectionNode section) {
                for (Node.ConstDeclNode constDecl : section.declarations) {
                    line("static final " + javaType(constDecl.resolvedType) + " " + javaIdentifier(constDecl.name)
                            + " = " + expr(constDecl.value) + ";");
                }
            }
        }
        line("");
    }

    private void emitRoutines(List<Node> declarations) {
        for (Node declaration : declarations) {
            if (declaration instanceof Node.ProcedureNode procedure) {
                emitProcedure(procedure);
            } else if (declaration instanceof Node.FunctionNode function) {
                emitFunction(function);
            }
        }
    }

    private void emitProcedure(Node.ProcedureNode procedure) {
        line("static void " + javaIdentifier(procedure.name) + "(" + params(procedure.params) + ") {");
        indent++;
        emitLocalDeclarations(procedure.declarations);
        emitBlockStatements(procedure.body);
        indent--;
        line("}");
        line("");
    }

    private void emitFunction(Node.FunctionNode function) {
        String previousFunctionName = currentFunctionName;
        currentFunctionName = function.name;
        Type returnType = function.returnType.resolvedType;
        line("static " + javaType(returnType) + " " + javaIdentifier(function.name) + "(" + params(function.params) + ") {");
        indent++;
        line(javaType(returnType) + " __result = " + defaultValue(returnType) + ";");
        emitLocalDeclarations(function.declarations);
        emitBlockStatements(function.body);
        line("return __result;");
        indent--;
        line("}");
        line("");
        currentFunctionName = previousFunctionName;
    }

    private String params(List<Node.ParamNode> params) {
        List<String> result = new ArrayList<>();
        for (Node.ParamNode param : params) {
            String type = javaType(param.typeNode.resolvedType);
            for (String name : param.names) {
                result.add(type + " " + javaIdentifier(name));
            }
        }
        return String.join(", ", result);
    }

    private void emitLocalDeclarations(List<Node> declarations) {
        for (Node declaration : declarations) {
            if (declaration instanceof Node.VarSectionNode section) {
                for (Node.VarDeclNode varDecl : section.declarations) {
                    Type type = varDecl.typeNode.resolvedType;
                    for (String name : varDecl.names) {
                        line(javaType(type) + " " + javaIdentifier(name) + " = " + defaultValue(type) + ";");
                    }
                }
            } else if (declaration instanceof Node.ConstSectionNode section) {
                for (Node.ConstDeclNode constDecl : section.declarations) {
                    line("final " + javaType(constDecl.resolvedType) + " " + javaIdentifier(constDecl.name)
                            + " = " + expr(constDecl.value) + ";");
                }
            }
        }
    }

    private void emitBlockStatements(Node.BlockNode block) {
        for (Node statement : block.statements) {
            emitStatement(statement);
        }
    }

    private void emitStatement(Node statement) {
        if (statement == null) return;
        if (statement instanceof Node.BlockNode block) {
            line("{");
            indent++;
            emitBlockStatements(block);
            indent--;
            line("}");
        } else if (statement instanceof Node.AssignNode assign) {
            line(lValue(assign.target) + " = " + expr(assign.value) + ";");
        } else if (statement instanceof Node.IfNode ifNode) {
            line("if (" + expr(ifNode.condition) + ") {");
            indent++;
            emitStatement(ifNode.thenBranch);
            indent--;
            if (ifNode.elseBranch != null) {
                line("} else {");
                indent++;
                emitStatement(ifNode.elseBranch);
                indent--;
            }
            line("}");
        } else if (statement instanceof Node.ForNode forNode) {
            String var = javaIdentifier(forNode.varName);
            String from = expr(forNode.fromExpr);
            String to = expr(forNode.toExpr);
            if (forNode.downTo) {
                line("for (" + var + " = " + from + "; " + var + " >= " + to + "; " + var + "--) {");
            } else {
                line("for (" + var + " = " + from + "; " + var + " <= " + to + "; " + var + "++) {");
            }
            indent++;
            emitStatement(forNode.body);
            indent--;
            line("}");
        } else if (statement instanceof Node.WhileNode whileNode) {
            line("while (" + expr(whileNode.condition) + ") {");
            indent++;
            emitStatement(whileNode.body);
            indent--;
            line("}");
        } else if (statement instanceof Node.RepeatNode repeatNode) {
            line("do {");
            indent++;
            for (Node item : repeatNode.statements) emitStatement(item);
            indent--;
            line("} while (!(" + expr(repeatNode.condition) + "));");
        } else if (statement instanceof Node.BreakNode) {
            line("break;");
        } else if (statement instanceof Node.ContinueNode) {
            line("continue;");
        } else if (statement instanceof Node.ExitNode) {
            line(currentFunctionName == null ? "return;" : "return __result;");
        } else if (statement instanceof Node.WriteNode writeNode) {
            emitWrite(writeNode);
        } else if (statement instanceof Node.ReadNode readNode) {
            for (Node arg : readNode.args) line(lValue(arg) + " = " + readExpr(arg.resolvedType) + ";");
        } else if (statement instanceof Node.SysFuncCallNode sysFunc) {
            emitSysStatement(sysFunc);
        } else if (statement instanceof Node.ProcCallNode call) {
            line(javaIdentifier(call.name) + "(" + args(call.args) + ");");
        }
    }

    private void emitWrite(Node.WriteNode writeNode) {
        if (writeNode.args.isEmpty()) {
            line(writeNode.newLine ? "System.out.println();" : "");
            return;
        }
        for (int i = 0; i < writeNode.args.size(); i++) {
            Node arg = writeNode.args.get(i);
            boolean last = i == writeNode.args.size() - 1;
            String method = writeNode.newLine && last ? "println" : "print";
            line("System.out." + method + "(" + expr(arg) + ");");
        }
    }

    private void emitSysStatement(Node.SysFuncCallNode call) {
        String name = call.funcName.toLowerCase();
        if (name.equals("inc")) {
            line(lValue(call.args.get(0)) + "++;");
        } else if (name.equals("dec")) {
            line(lValue(call.args.get(0)) + "--;");
        } else {
            line(expr(call) + ";");
        }
    }

    private String expr(Node node) {
        if (node instanceof Node.IntLiteralNode literal) return Integer.toString(literal.value);
        if (node instanceof Node.RealLiteralNode literal) return Double.toString(literal.value);
        if (node instanceof Node.BoolLiteralNode literal) return Boolean.toString(literal.value);
        if (node instanceof Node.CharLiteralNode literal) return charLiteral(literal.value);
        if (node instanceof Node.StringLiteralNode literal) return stringLiteral(literal.value);
        if (node instanceof Node.IdentifierNode identifier) return identifierExpr(identifier);
        if (node instanceof Node.ArrayAccessNode access) return arrayAccess(access);
        if (node instanceof Node.UnaryOpNode unary) return unaryExpr(unary);
        if (node instanceof Node.BinaryOpNode binary) return binaryExpr(binary);
        if (node instanceof Node.ProcCallNode call) return javaIdentifier(call.name) + "(" + args(call.args) + ")";
        if (node instanceof Node.SysFuncCallNode sysFunc) return sysExpr(sysFunc);
        return "/* unsupported */";
    }

    private String identifierExpr(Node.IdentifierNode identifier) {
        if (currentFunctionName != null && identifier.name.equalsIgnoreCase(currentFunctionName)) return "__result";
        return javaIdentifier(identifier.name);
    }

    private String unaryExpr(Node.UnaryOpNode unary) {
        String op = unary.op.equalsIgnoreCase("not") ? "!" : unary.op;
        return "(" + op + expr(unary.operand) + ")";
    }

    private String binaryExpr(Node.BinaryOpNode binary) {
        String op = binary.op.toLowerCase();
        String left = expr(binary.left);
        String right = expr(binary.right);
        if (op.equals("+") && binary.resolvedType == Type.STRING) {
            return "(String.valueOf(" + left + ") + String.valueOf(" + right + "))";
        }
        if (op.equals("=") || op.equals("<>")) {
            if (binary.left.resolvedType == Type.STRING || binary.right.resolvedType == Type.STRING) {
                String equals = "java.util.Objects.equals(" + left + ", " + right + ")";
                return op.equals("=") ? equals : "(!" + equals + ")";
            }
            return "(" + left + " " + (op.equals("=") ? "==" : "!=") + " " + right + ")";
        }

        String javaOp = switch (op) {
            case "and" -> "&&";
            case "or" -> "||";
            case "xor" -> "^";
            case "div", "mod" -> op.equals("div") ? "/" : "%";
            default -> op;
        };
        return "(" + left + " " + javaOp + " " + right + ")";
    }

    private String sysExpr(Node.SysFuncCallNode call) {
        String name = call.funcName.toLowerCase();
        return switch (name) {
            case "abs" -> "Math.abs(" + expr(call.args.get(0)) + ")";
            case "length" -> expr(call.args.get(0)) + ".length" + (call.args.get(0).resolvedType == Type.STRING ? "()" : "");
            case "pos" -> "(" + expr(call.args.get(1)) + ".indexOf(String.valueOf(" + expr(call.args.get(0)) + ")) + 1)";
            case "copy" -> "__copy(" + expr(call.args.get(0)) + ", " + expr(call.args.get(1)) + ", " + expr(call.args.get(2)) + ")";
            default -> "/* unsupported system function */";
        };
    }

    private String lValue(Node node) {
        if (node instanceof Node.IdentifierNode identifier) return identifierExpr(identifier);
        if (node instanceof Node.ArrayAccessNode access) return arrayAccess(access);
        return expr(node);
    }

    private String arrayAccess(Node.ArrayAccessNode access) {
        String base = expr(access.array);
        String index = expr(access.index);
        if (access.array.resolvedType == Type.STRING) {
            return base + ".charAt((" + index + ") - 1)";
        }
        Type arrayType = access.array.resolvedType;
        int lower = arrayType != null && arrayType.kind() == Type.Kind.ARRAY ? arrayType.lowerBound() : 0;
        return base + "[(" + index + ") - " + lower + "]";
    }

    private String args(List<Node> args) {
        List<String> result = new ArrayList<>();
        for (Node arg : args) result.add(expr(arg));
        return String.join(", ", result);
    }

    private String readExpr(Type type) {
        if (type == Type.INTEGER) return "__scanner.nextInt()";
        if (type == Type.DOUBLE) return "__scanner.nextDouble()";
        if (type == Type.BOOLEAN) return "__scanner.nextBoolean()";
        if (type == Type.CHAR) return "__scanner.next().charAt(0)";
        return "__scanner.next()";
    }

    private void emitHelpers() {
        line("");
        line("private static String __copy(String s, int start, int length) {");
        indent++;
        line("int from = Math.max(0, start - 1);");
        line("int to = Math.min(s.length(), from + Math.max(0, length));");
        line("return s.substring(from, to);");
        indent--;
        line("}");
    }

    private String javaType(Type type) {
        if (type == null || type == Type.ERROR) return "Object";
        return switch (type.kind()) {
            case INTEGER -> "int";
            case DOUBLE -> "double";
            case BOOLEAN -> "boolean";
            case CHAR -> "char";
            case STRING -> "String";
            case ARRAY -> javaType(type.elementType()) + "[]";
            case VOID -> "void";
            case ERROR -> "Object";
        };
    }

    private String defaultValue(Type type) {
        if (type == null || type == Type.ERROR) return "null";
        return switch (type.kind()) {
            case INTEGER -> "0";
            case DOUBLE -> "0.0";
            case BOOLEAN -> "false";
            case CHAR -> "'\\0'";
            case STRING -> "\"\"";
            case ARRAY -> "new " + javaType(type.elementType()) + "[" + type.arraySize() + "]";
            case VOID, ERROR -> "null";
        };
    }

    private String javaIdentifier(String raw) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (i == 0) result.append(Character.isJavaIdentifierStart(ch) ? ch : '_');
            else result.append(Character.isJavaIdentifierPart(ch) ? ch : '_');
        }
        String value = result.toString();
        if (value.isEmpty()) value = "_";
        return switch (value) {
            case "class", "public", "private", "static", "void", "int", "double", "boolean",
                    "char", "if", "else", "for", "while", "do", "break", "continue",
                    "return", "new", "final", "String" -> value + "_";
            default -> value;
        };
    }

    private String stringLiteral(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    private String charLiteral(char value) {
        return switch (value) {
            case '\\' -> "'\\\\'";
            case '\'' -> "'\\''";
            case '\n' -> "'\\n'";
            case '\r' -> "'\\r'";
            case '\t' -> "'\\t'";
            default -> "'" + value + "'";
        };
    }

    private void line(String text) {
        if (text.isEmpty()) {
            out.append(System.lineSeparator());
            return;
        }
        out.append("    ".repeat(indent)).append(text).append(System.lineSeparator());
    }
}
