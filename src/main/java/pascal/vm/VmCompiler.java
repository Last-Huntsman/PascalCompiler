package pascal.vm;

import pascal.ast.Node;
import pascal.semantic.Type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VmCompiler {
    private static final String ENTRY_ROUTINE = "__main";

    private final List<VmSlot> globals = new ArrayList<>();
    private final Map<String, Integer> globalSlots = new HashMap<>();
    private final List<VmRoutine> routines = new ArrayList<>();

    public VmProgram compile(Node.ProgramNode program) {
        globals.clear();
        globalSlots.clear();
        routines.clear();

        allocateGlobals(program.declarations);
        compileRoutines(program.declarations);
        compileEntry(program);
        return new VmProgram(program.name, ENTRY_ROUTINE, globals, routines);
    }

    private void allocateGlobals(List<Node> declarations) {
        for (Node declaration : declarations) {
            if (declaration instanceof Node.VarSectionNode section) {
                for (Node.VarDeclNode varDecl : section.declarations) {
                    for (String name : varDecl.names) {
                        addGlobal(name, varDecl.typeNode.resolvedType);
                    }
                }
            } else if (declaration instanceof Node.ConstSectionNode section) {
                for (Node.ConstDeclNode constDecl : section.declarations) {
                    addGlobal(constDecl.name, constDecl.resolvedType);
                }
            }
        }
    }

    private void addGlobal(String name, Type type) {
        String key = normalize(name);
        if (globalSlots.containsKey(key)) {
            return;
        }
        globalSlots.put(key, globals.size());
        globals.add(new VmSlot(name, type));
    }

    private void compileRoutines(List<Node> declarations) {
        for (Node declaration : declarations) {
            if (declaration instanceof Node.ProcedureNode procedure) {
                routines.add(compileRoutine(procedure.name, procedure.params, Type.VOID, procedure.declarations, procedure.body));
            } else if (declaration instanceof Node.FunctionNode function) {
                routines.add(compileRoutine(function.name, function.params, function.returnType.resolvedType, function.declarations, function.body));
            }
        }
    }

    private void compileEntry(Node.ProgramNode program) {
        RoutineContext context = new RoutineContext(ENTRY_ROUTINE, Type.VOID, 0, -1);
        emitConstInitializers(program.declarations, context, true);
        compileStatement(program.body, context);
        context.emitter.emit(VmOpCode.HALT);
        routines.add(context.build());
    }

    private VmRoutine compileRoutine(String name,
                                     List<Node.ParamNode> params,
                                     Type returnType,
                                     List<Node> declarations,
                                     Node.BlockNode body) {
        RoutineContext context = new RoutineContext(name, returnType, paramCount(params), -1);

        for (Node.ParamNode param : params) {
            for (String paramName : param.names) {
                context.addLocal(paramName, param.typeNode.resolvedType);
            }
        }
        if (returnType != Type.VOID) {
            context.returnSlotIndex = context.addLocal(name, returnType);
        }
        allocateRoutineLocals(declarations, context);
        emitConstInitializers(declarations, context, false);
        compileStatement(body, context);
        context.emitter.emit(VmOpCode.RET);
        return context.build();
    }

    private int paramCount(List<Node.ParamNode> params) {
        int count = 0;
        for (Node.ParamNode param : params) {
            count += param.names.size();
        }
        return count;
    }

    private void allocateRoutineLocals(List<Node> declarations, RoutineContext context) {
        for (Node declaration : declarations) {
            if (declaration instanceof Node.VarSectionNode section) {
                for (Node.VarDeclNode varDecl : section.declarations) {
                    for (String name : varDecl.names) {
                        context.addLocal(name, varDecl.typeNode.resolvedType);
                    }
                }
            } else if (declaration instanceof Node.ConstSectionNode section) {
                for (Node.ConstDeclNode constDecl : section.declarations) {
                    context.addLocal(constDecl.name, constDecl.resolvedType);
                }
            }
        }
    }

    private void emitConstInitializers(List<Node> declarations, RoutineContext context, boolean global) {
        for (Node declaration : declarations) {
            if (declaration instanceof Node.ConstSectionNode section) {
                for (Node.ConstDeclNode constDecl : section.declarations) {
                    compileExpression(constDecl.value, context);
                    if (global) {
                        emitStoreNamed(constDecl.name, context);
                    } else {
                        emitStoreNamed(constDecl.name, context);
                    }
                }
            }
        }
    }

    private void compileStatement(Node statement, RoutineContext context) {
        if (statement == null) {
            return;
        }
        if (statement instanceof Node.BlockNode block) {
            for (Node item : block.statements) {
                compileStatement(item, context);
            }
            return;
        }
        if (statement instanceof Node.AssignNode assign) {
            compileExpression(assign.value, context);
            compileStoreFromTop(assign.target, context);
            return;
        }
        if (statement instanceof Node.IfNode ifNode) {
            Label elseLabel = context.emitter.label();
            Label endLabel = context.emitter.label();
            compileExpression(ifNode.condition, context);
            context.emitter.emitJump(VmOpCode.JMP_IF_FALSE, elseLabel);
            compileStatement(ifNode.thenBranch, context);
            if (ifNode.elseBranch != null) {
                context.emitter.emitJump(VmOpCode.JMP, endLabel);
                context.emitter.mark(elseLabel);
                compileStatement(ifNode.elseBranch, context);
                context.emitter.mark(endLabel);
            } else {
                context.emitter.mark(elseLabel);
            }
            return;
        }
        if (statement instanceof Node.ForNode forNode) {
            SlotRef counter = resolveNamed(forNode.varName, context);
            int limitSlot = context.reserveTemp(Type.INTEGER);
            compileExpression(forNode.fromExpr, context);
            emitStore(counter, context);
            compileExpression(forNode.toExpr, context);
            context.emitter.emit(VmOpCode.STORE_LOCAL, limitSlot);

            Label condition = context.emitter.label();
            Label step = context.emitter.label();
            Label end = context.emitter.label();

            context.emitter.mark(condition);
            emitLoad(counter, context);
            context.emitter.emit(VmOpCode.LOAD_LOCAL, limitSlot);
            context.emitter.emit(forNode.downTo ? VmOpCode.CMP_GE : VmOpCode.CMP_LE);
            context.emitter.emitJump(VmOpCode.JMP_IF_FALSE, end);

            context.loopStack.push(new LoopContext(end, step));
            compileStatement(forNode.body, context);
            context.loopStack.pop();

            context.emitter.mark(step);
            emitLoad(counter, context);
            context.emitter.emit(VmOpCode.PUSH_CONST, 1);
            context.emitter.emit(forNode.downTo ? VmOpCode.SUB : VmOpCode.ADD);
            emitStore(counter, context);
            context.emitter.emitJump(VmOpCode.JMP, condition);
            context.emitter.mark(end);
            return;
        }
        if (statement instanceof Node.WhileNode whileNode) {
            Label start = context.emitter.label();
            Label end = context.emitter.label();
            context.emitter.mark(start);
            compileExpression(whileNode.condition, context);
            context.emitter.emitJump(VmOpCode.JMP_IF_FALSE, end);
            context.loopStack.push(new LoopContext(end, start));
            compileStatement(whileNode.body, context);
            context.loopStack.pop();
            context.emitter.emitJump(VmOpCode.JMP, start);
            context.emitter.mark(end);
            return;
        }
        if (statement instanceof Node.RepeatNode repeatNode) {
            Label start = context.emitter.label();
            Label end = context.emitter.label();
            Label condition = context.emitter.label();
            context.emitter.mark(start);
            context.loopStack.push(new LoopContext(end, condition));
            for (Node item : repeatNode.statements) {
                compileStatement(item, context);
            }
            context.loopStack.pop();
            context.emitter.mark(condition);
            compileExpression(repeatNode.condition, context);
            context.emitter.emitJump(VmOpCode.JMP_IF_FALSE, start);
            context.emitter.mark(end);
            return;
        }
        if (statement instanceof Node.BreakNode) {
            context.emitter.emitJump(VmOpCode.JMP, context.loopStack.peek().breakLabel);
            return;
        }
        if (statement instanceof Node.ContinueNode) {
            context.emitter.emitJump(VmOpCode.JMP, context.loopStack.peek().continueLabel);
            return;
        }
        if (statement instanceof Node.ExitNode) {
            context.emitter.emit(VmOpCode.RET);
            return;
        }
        if (statement instanceof Node.WriteNode writeNode) {
            if (writeNode.args.isEmpty()) {
                context.emitter.emit(VmOpCode.PUSH_CONST, "");
                context.emitter.emit(VmOpCode.PRINTLN);
                return;
            }
            for (int i = 0; i < writeNode.args.size(); i++) {
                compileExpression(writeNode.args.get(i), context);
                boolean last = i == writeNode.args.size() - 1;
                context.emitter.emit(writeNode.newLine && last ? VmOpCode.PRINTLN : VmOpCode.PRINT);
            }
            return;
        }
        if (statement instanceof Node.ReadNode readNode) {
            if (readNode.args.isEmpty()) {
                if (readNode.newLine) {
                    context.emitter.emit(VmOpCode.READLN, VmTypeCodec.encode(Type.VOID));
                }
                return;
            }
            for (int i = 0; i < readNode.args.size(); i++) {
                Node arg = readNode.args.get(i);
                boolean last = i == readNode.args.size() - 1;
                context.emitter.emit(readNode.newLine && last ? VmOpCode.READLN : VmOpCode.READ,
                        VmTypeCodec.encode(arg.resolvedType));
                compileStoreFromTop(arg, context);
            }
            return;
        }
        if (statement instanceof Node.SysFuncCallNode sysFunc) {
            compileSystemStatement(sysFunc, context);
            return;
        }
        if (statement instanceof Node.ProcCallNode call) {
            compileCall(call, context);
        }
    }

    private void compileSystemStatement(Node.SysFuncCallNode call, RoutineContext context) {
        String name = call.funcName.toLowerCase();
        if (name.equals("inc") || name.equals("dec")) {
            Node target = call.args.get(0);
            compileExpression(target, context);
            context.emitter.emit(VmOpCode.PUSH_CONST, 1);
            context.emitter.emit(name.equals("inc") ? VmOpCode.ADD : VmOpCode.SUB);
            compileStoreFromTop(target, context);
            return;
        }
        compileExpression(call, context);
    }

    private void compileExpression(Node expression, RoutineContext context) {
        if (expression instanceof Node.IntLiteralNode literal) {
            context.emitter.emit(VmOpCode.PUSH_CONST, literal.value);
            return;
        }
        if (expression instanceof Node.RealLiteralNode literal) {
            context.emitter.emit(VmOpCode.PUSH_CONST, literal.value);
            return;
        }
        if (expression instanceof Node.BoolLiteralNode literal) {
            context.emitter.emit(VmOpCode.PUSH_CONST, literal.value);
            return;
        }
        if (expression instanceof Node.CharLiteralNode literal) {
            context.emitter.emit(VmOpCode.PUSH_CONST, literal.value);
            return;
        }
        if (expression instanceof Node.StringLiteralNode literal) {
            context.emitter.emit(VmOpCode.PUSH_CONST, literal.value);
            return;
        }
        if (expression instanceof Node.IdentifierNode identifier) {
            emitLoad(resolveNamed(identifier.name, context), context);
            return;
        }
        if (expression instanceof Node.ArrayAccessNode access) {
            compileExpression(access.array, context);
            compileExpression(access.index, context);
            context.emitter.emit(VmOpCode.LOAD_INDEX, lowerBound(access));
            return;
        }
        if (expression instanceof Node.UnaryOpNode unary) {
            compileExpression(unary.operand, context);
            if (unary.op.equals("-")) {
                context.emitter.emit(VmOpCode.NEG);
            } else if (unary.op.equalsIgnoreCase("not")) {
                context.emitter.emit(VmOpCode.NOT);
            }
            return;
        }
        if (expression instanceof Node.BinaryOpNode binary) {
            compileExpression(binary.left, context);
            compileExpression(binary.right, context);
            emitBinary(binary, context);
            return;
        }
        if (expression instanceof Node.ProcCallNode call) {
            compileCall(call, context);
            return;
        }
        if (expression instanceof Node.SysFuncCallNode call) {
            compileSystemExpression(call, context);
        }
    }

    private void compileCall(Node.ProcCallNode call, RoutineContext context) {
        for (Node arg : call.args) {
            compileExpression(arg, context);
        }
        context.emitter.emit(VmOpCode.CALL, call.name);
    }

    private void compileSystemExpression(Node.SysFuncCallNode call, RoutineContext context) {
        String name = call.funcName.toLowerCase();
        switch (name) {
            case "abs" -> {
                compileExpression(call.args.get(0), context);
                context.emitter.emit(VmOpCode.ABS);
            }
            case "length" -> {
                Node arg = call.args.get(0);
                compileExpression(arg, context);
                context.emitter.emit(arg.resolvedType == Type.STRING ? VmOpCode.STRING_LENGTH : VmOpCode.ARRAY_LENGTH);
            }
            case "pos" -> {
                compileExpression(call.args.get(0), context);
                compileExpression(call.args.get(1), context);
                context.emitter.emit(VmOpCode.POS);
            }
            case "copy" -> {
                compileExpression(call.args.get(0), context);
                compileExpression(call.args.get(1), context);
                compileExpression(call.args.get(2), context);
                context.emitter.emit(VmOpCode.COPY);
            }
            default -> throw new IllegalArgumentException("Unsupported system function in expression: " + call.funcName);
        }
    }

    private void emitBinary(Node.BinaryOpNode binary, RoutineContext context) {
        String op = binary.op.toLowerCase();
        switch (op) {
            case "+" -> context.emitter.emit(VmOpCode.ADD);
            case "-" -> context.emitter.emit(VmOpCode.SUB);
            case "*" -> context.emitter.emit(VmOpCode.MUL);
            case "/", "div" -> context.emitter.emit(VmOpCode.DIV);
            case "mod" -> context.emitter.emit(VmOpCode.MOD);
            case "and" -> context.emitter.emit(VmOpCode.AND);
            case "or" -> context.emitter.emit(VmOpCode.OR);
            case "xor" -> context.emitter.emit(VmOpCode.XOR);
            case "=" -> context.emitter.emit(VmOpCode.CMP_EQ);
            case "<>" -> context.emitter.emit(VmOpCode.CMP_NE);
            case "<" -> context.emitter.emit(VmOpCode.CMP_LT);
            case "<=" -> context.emitter.emit(VmOpCode.CMP_LE);
            case ">" -> context.emitter.emit(VmOpCode.CMP_GT);
            case ">=" -> context.emitter.emit(VmOpCode.CMP_GE);
            default -> throw new IllegalArgumentException("Unsupported binary operator: " + binary.op);
        }
    }

    private void compileStoreFromTop(Node target, RoutineContext context) {
        if (target instanceof Node.IdentifierNode identifier) {
            emitStore(resolveNamed(identifier.name, context), context);
            return;
        }
        Node.ArrayAccessNode access = (Node.ArrayAccessNode) target;
        int temp = context.reserveTemp(access.resolvedType);
        context.emitter.emit(VmOpCode.STORE_LOCAL, temp);
        compileExpression(access.array, context);
        compileExpression(access.index, context);
        context.emitter.emit(VmOpCode.LOAD_LOCAL, temp);
        context.emitter.emit(VmOpCode.STORE_INDEX, lowerBound(access));
    }

    private int lowerBound(Node.ArrayAccessNode access) {
        return access.array.resolvedType == Type.STRING ? 1 : access.array.resolvedType.lowerBound();
    }

    private SlotRef resolveNamed(String name, RoutineContext context) {
        Integer local = context.locals.get(normalize(name));
        if (local != null) {
            return new SlotRef(true, local);
        }
        Integer global = globalSlots.get(normalize(name));
        if (global != null) {
            return new SlotRef(false, global);
        }
        throw new IllegalArgumentException("Unknown slot for name " + name);
    }

    private void emitLoad(SlotRef slot, RoutineContext context) {
        context.emitter.emit(slot.local ? VmOpCode.LOAD_LOCAL : VmOpCode.LOAD_GLOBAL, slot.index);
    }

    private void emitStore(SlotRef slot, RoutineContext context) {
        context.emitter.emit(slot.local ? VmOpCode.STORE_LOCAL : VmOpCode.STORE_GLOBAL, slot.index);
    }

    private void emitStoreNamed(String name, RoutineContext context) {
        emitStore(resolveNamed(name, context), context);
    }

    private String normalize(String name) {
        return name.toLowerCase();
    }

    private static final class SlotRef {
        private final boolean local;
        private final int index;

        private SlotRef(boolean local, int index) {
            this.local = local;
            this.index = index;
        }
    }

    private static final class LoopContext {
        private final Label breakLabel;
        private final Label continueLabel;

        private LoopContext(Label breakLabel, Label continueLabel) {
            this.breakLabel = breakLabel;
            this.continueLabel = continueLabel;
        }
    }

    private static final class RoutineContext {
        private final String name;
        private final Type returnType;
        private final int parameterCount;
        private final List<VmSlot> slots = new ArrayList<>();
        private final Map<String, Integer> locals = new HashMap<>();
        private final CodeEmitter emitter = new CodeEmitter();
        private final Deque<LoopContext> loopStack = new ArrayDeque<>();
        private int tempCounter;
        private int returnSlotIndex;

        private RoutineContext(String name, Type returnType, int parameterCount, int returnSlotIndex) {
            this.name = name;
            this.returnType = returnType;
            this.parameterCount = parameterCount;
            this.returnSlotIndex = returnSlotIndex;
        }

        private int addLocal(String name, Type type) {
            String key = name.toLowerCase();
            Integer existing = locals.get(key);
            if (existing != null) {
                return existing;
            }
            int index = slots.size();
            slots.add(new VmSlot(name, type));
            locals.put(key, index);
            return index;
        }

        private int reserveTemp(Type type) {
            return addLocal("__tmp" + tempCounter++, type);
        }

        private VmRoutine build() {
            return new VmRoutine(name, returnType, parameterCount, returnSlotIndex, slots, emitter.build());
        }
    }

    private static final class Label {
        private int position = -1;
    }

    private static final class PendingInstruction {
        private final VmOpCode opcode;
        private final List<Object> operands;

        private PendingInstruction(VmOpCode opcode, List<Object> operands) {
            this.opcode = opcode;
            this.operands = operands;
        }
    }

    private static final class CodeEmitter {
        private final List<PendingInstruction> instructions = new ArrayList<>();

        private Label label() {
            return new Label();
        }

        private void mark(Label label) {
            label.position = instructions.size();
        }

        private void emit(VmOpCode opcode, Object... operands) {
            List<Object> items = new ArrayList<>(operands.length);
            for (Object operand : operands) {
                items.add(operand);
            }
            instructions.add(new PendingInstruction(opcode, items));
        }

        private void emitJump(VmOpCode opcode, Label label) {
            emit(opcode, label);
        }

        private List<VmInstruction> build() {
            List<VmInstruction> result = new ArrayList<>(instructions.size());
            for (PendingInstruction pending : instructions) {
                List<Object> operands = new ArrayList<>(pending.operands.size());
                for (Object operand : pending.operands) {
                    if (operand instanceof Label label) {
                        operands.add(label.position);
                    } else {
                        operands.add(operand);
                    }
                }
                result.add(new VmInstruction(pending.opcode, operands));
            }
            return result;
        }
    }
}
