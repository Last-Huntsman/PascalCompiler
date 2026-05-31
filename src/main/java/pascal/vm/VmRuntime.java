package pascal.vm;

import pascal.semantic.Type;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Scanner;

public class VmRuntime {
    public void execute(VmProgram program, InputStream in, PrintStream out) {
        Scanner scanner = new Scanner(in);
        Deque<Object> stack = new ArrayDeque<>();
        Deque<Frame> callers = new ArrayDeque<>();
        Object[] globals = createStorage(program.globals());
        Frame current = new Frame(program.routine(program.entryRoutine()), createStorage(program.routine(program.entryRoutine()).slots()));

        while (current != null) {
            VmInstruction instruction = current.routine.instructions().get(current.ip++);
            switch (instruction.opcode()) {
                case PUSH_CONST -> stack.push(instruction.operands().get(0));
                case LOAD_GLOBAL -> stack.push(globals[(Integer) instruction.operands().get(0)]);
                case STORE_GLOBAL -> globals[(Integer) instruction.operands().get(0)] = stack.pop();
                case LOAD_LOCAL -> stack.push(current.slots[(Integer) instruction.operands().get(0)]);
                case STORE_LOCAL -> current.slots[(Integer) instruction.operands().get(0)] = stack.pop();
                case LOAD_INDEX -> {
                    int lowerBound = (Integer) instruction.operands().get(0);
                    int index = asInt(stack.pop());
                    Object container = stack.pop();
                    if (container instanceof String text) {
                        stack.push(text.charAt(index - 1));
                    } else {
                        Object[] array = (Object[]) container;
                        stack.push(array[index - lowerBound]);
                    }
                }
                case STORE_INDEX -> {
                    int lowerBound = (Integer) instruction.operands().get(0);
                    Object value = stack.pop();
                    int index = asInt(stack.pop());
                    Object[] array = (Object[]) stack.pop();
                    array[index - lowerBound] = value;
                }
                case ARRAY_LENGTH -> stack.push(((Object[]) stack.pop()).length);
                case STRING_LENGTH -> stack.push(String.valueOf(stack.pop()).length());
                case ADD -> {
                    Object right = stack.pop();
                    Object left = stack.pop();
                    stack.push(add(left, right));
                }
                case SUB -> {
                    Object right = stack.pop();
                    Object left = stack.pop();
                    stack.push(arithmetic(left, right, "-"));
                }
                case MUL -> {
                    Object right = stack.pop();
                    Object left = stack.pop();
                    stack.push(arithmetic(left, right, "*"));
                }
                case DIV -> {
                    Object right = stack.pop();
                    Object left = stack.pop();
                    stack.push(arithmetic(left, right, "/"));
                }
                case MOD -> {
                    int right = asInt(stack.pop());
                    int left = asInt(stack.pop());
                    stack.push(left % right);
                }
                case NEG -> {
                    Object value = stack.pop();
                    stack.push(value instanceof Double ? -((Double) value) : -asInt(value));
                }
                case NOT -> stack.push(!(Boolean) stack.pop());
                case AND -> {
                    boolean right = (Boolean) stack.pop();
                    boolean left = (Boolean) stack.pop();
                    stack.push(left && right);
                }
                case OR -> {
                    boolean right = (Boolean) stack.pop();
                    boolean left = (Boolean) stack.pop();
                    stack.push(left || right);
                }
                case XOR -> {
                    boolean right = (Boolean) stack.pop();
                    boolean left = (Boolean) stack.pop();
                    stack.push(left ^ right);
                }
                case CMP_EQ, CMP_NE, CMP_LT, CMP_LE, CMP_GT, CMP_GE -> {
                    Object right = stack.pop();
                    Object left = stack.pop();
                    stack.push(compare(left, right, instruction.opcode()));
                }
                case JMP -> current.ip = (Integer) instruction.operands().get(0);
                case JMP_IF_FALSE -> {
                    boolean condition = (Boolean) stack.pop();
                    if (!condition) {
                        current.ip = (Integer) instruction.operands().get(0);
                    }
                }
                case CALL -> {
                    String name = (String) instruction.operands().get(0);
                    VmRoutine callee = program.routine(name);
                    Object[] slots = createStorage(callee.slots());
                    for (int i = callee.parameterCount() - 1; i >= 0; i--) {
                        slots[i] = stack.pop();
                    }
                    callers.push(current);
                    current = new Frame(callee, slots);
                }
                case RET -> {
                    Object result = null;
                    if (current.routine.returnSlotIndex() >= 0) {
                        result = current.slots[current.routine.returnSlotIndex()];
                    }
                    if (callers.isEmpty()) {
                        current = null;
                    } else {
                        current = callers.pop();
                        if (result != null || current != null) {
                            if (result != null) {
                                stack.push(result);
                            }
                        }
                    }
                }
                case HALT -> {
                    return;
                }
                case PRINT -> out.print(printable(stack.pop()));
                case PRINTLN -> out.println(printable(stack.pop()));
                case READ -> stack.push(readScalar((String) instruction.operands().get(0), scanner, false));
                case READLN -> {
                    String typeName = (String) instruction.operands().get(0);
                    if (typeName.equals("void")) {
                        if (scanner.hasNextLine()) {
                            scanner.nextLine();
                        }
                    } else {
                        stack.push(readScalar(typeName, scanner, true));
                    }
                }
                case ABS -> {
                    Object value = stack.pop();
                    stack.push(value instanceof Double ? Math.abs((Double) value) : Math.abs(asInt(value)));
                }
                case POS -> {
                    String haystack = String.valueOf(stack.pop());
                    String needle = String.valueOf(stack.pop());
                    stack.push(haystack.indexOf(needle) + 1);
                }
                case COPY -> {
                    int length = asInt(stack.pop());
                    int start = asInt(stack.pop());
                    String source = String.valueOf(stack.pop());
                    int from = Math.max(0, start - 1);
                    int to = Math.min(source.length(), from + Math.max(0, length));
                    stack.push(source.substring(from, to));
                }
            }
        }
    }

    private Object[] createStorage(List<VmSlot> slots) {
        Object[] storage = new Object[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            storage[i] = defaultValue(slots.get(i).type());
        }
        return storage;
    }

    private Object defaultValue(Type type) {
        if (type == Type.INTEGER) return 0;
        if (type == Type.DOUBLE) return 0.0;
        if (type == Type.BOOLEAN) return false;
        if (type == Type.CHAR) return '\0';
        if (type == Type.STRING) return "";
        if (type != null && type.kind() == Type.Kind.ARRAY) {
            Object[] array = new Object[type.arraySize()];
            Object elementDefault = defaultValue(type.elementType());
            Arrays.fill(array, elementDefault);
            return array;
        }
        return null;
    }

    private Object add(Object left, Object right) {
        if (left instanceof String || right instanceof String || left instanceof Character || right instanceof Character) {
            return String.valueOf(left) + right;
        }
        return arithmetic(left, right, "+");
    }

    private Object arithmetic(Object left, Object right, String op) {
        if (left instanceof Double || right instanceof Double) {
            double l = asDouble(left);
            double r = asDouble(right);
            return switch (op) {
                case "+" -> l + r;
                case "-" -> l - r;
                case "*" -> l * r;
                case "/" -> l / r;
                default -> throw new IllegalArgumentException("Unsupported op " + op);
            };
        }
        int l = asInt(left);
        int r = asInt(right);
        return switch (op) {
            case "+" -> l + r;
            case "-" -> l - r;
            case "*" -> l * r;
            case "/" -> l / r;
            default -> throw new IllegalArgumentException("Unsupported op " + op);
        };
    }

    private boolean compare(Object left, Object right, VmOpCode opcode) {
        int comparison;
        if (left instanceof String || right instanceof String) {
            comparison = String.valueOf(left).compareTo(String.valueOf(right));
        } else if (left instanceof Character || right instanceof Character) {
            comparison = Character.compare(asChar(left), asChar(right));
        } else if (left instanceof Boolean || right instanceof Boolean) {
            comparison = Boolean.compare((Boolean) left, (Boolean) right);
        } else if (left instanceof Double || right instanceof Double) {
            comparison = Double.compare(asDouble(left), asDouble(right));
        } else {
            comparison = Integer.compare(asInt(left), asInt(right));
        }
        return switch (opcode) {
            case CMP_EQ -> comparison == 0;
            case CMP_NE -> comparison != 0;
            case CMP_LT -> comparison < 0;
            case CMP_LE -> comparison <= 0;
            case CMP_GT -> comparison > 0;
            case CMP_GE -> comparison >= 0;
            default -> throw new IllegalArgumentException("Unsupported compare op " + opcode);
        };
    }

    private Object readScalar(String typeName, Scanner scanner, boolean consumeLine) {
        Type type = VmTypeCodec.decode(typeName);
        Object value;
        if (type == Type.INTEGER) {
            value = scanner.nextInt();
        } else if (type == Type.DOUBLE) {
            value = scanner.nextDouble();
        } else if (type == Type.BOOLEAN) {
            value = scanner.nextBoolean();
        } else if (type == Type.CHAR) {
            value = scanner.next().charAt(0);
        } else {
            value = scanner.next();
        }
        if (consumeLine && scanner.hasNextLine()) {
            scanner.nextLine();
        }
        return value;
    }

    private String printable(Object value) {
        return String.valueOf(value);
    }

    private int asInt(Object value) {
        if (value instanceof Integer number) return number;
        if (value instanceof Double number) return number.intValue();
        throw new IllegalArgumentException("Expected integer-compatible value");
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        throw new IllegalArgumentException("Expected numeric value");
    }

    private char asChar(Object value) {
        if (value instanceof Character ch) return ch;
        return String.valueOf(value).charAt(0);
    }

    private static final class Frame {
        private final VmRoutine routine;
        private final Object[] slots;
        private int ip;

        private Frame(VmRoutine routine, Object[] slots) {
            this.routine = routine;
            this.slots = slots;
            this.ip = 0;
        }
    }
}
