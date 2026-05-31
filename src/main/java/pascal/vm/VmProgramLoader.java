package pascal.vm;

import pascal.semantic.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class VmProgramLoader {
    public VmProgram load(Path path) throws IOException {
        return load(Files.readString(path, StandardCharsets.UTF_8));
    }

    public VmProgram load(String text) {
        String[] lines = text.split("\\R");
        String programName = null;
        String entryRoutine = null;
        List<VmSlot> globals = new ArrayList<>();
        List<VmRoutine> routines = new ArrayList<>();
        RoutineBuilder current = null;

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            List<String> tokens = VmTextCodec.tokenize(line);
            if (tokens.isEmpty()) {
                continue;
            }
            switch (tokens.get(0)) {
                case "program" -> programName = tokens.get(1);
                case "entry" -> entryRoutine = tokens.get(1);
                case "globals" -> { }
                case "global" -> globals.add(new VmSlot(tokens.get(2), VmTypeCodec.decode(tokens.get(3))));
                case "routine" -> current = new RoutineBuilder(tokens);
                case "slot" -> {
                    if (current == null) throw new IllegalArgumentException("slot outside routine");
                    current.slots.add(new VmSlot(tokens.get(2), VmTypeCodec.decode(tokens.get(3))));
                }
                case "instr" -> {
                    if (current == null) throw new IllegalArgumentException("instr outside routine");
                    current.instructions.add(parseInstruction(tokens));
                }
                case "end" -> {
                    if (current == null) throw new IllegalArgumentException("end outside routine");
                    routines.add(current.build());
                    current = null;
                }
                default -> throw new IllegalArgumentException("Unknown VM line: " + line);
            }
        }

        if (programName == null || entryRoutine == null) {
            throw new IllegalArgumentException("VM program header is incomplete");
        }
        return new VmProgram(programName, entryRoutine, globals, routines);
    }

    private VmInstruction parseInstruction(List<String> tokens) {
        VmOpCode opcode = VmOpCode.valueOf(tokens.get(1));
        return switch (opcode) {
            case PUSH_CONST -> parsePushConst(tokens);
            case LOAD_GLOBAL, STORE_GLOBAL, LOAD_LOCAL, STORE_LOCAL, LOAD_INDEX, STORE_INDEX, JMP, JMP_IF_FALSE ->
                    new VmInstruction(opcode, Integer.parseInt(tokens.get(2)));
            case CALL, READ, READLN -> new VmInstruction(opcode, tokens.get(2));
            default -> new VmInstruction(opcode);
        };
    }

    private VmInstruction parsePushConst(List<String> tokens) {
        Type type = VmTypeCodec.decode(tokens.get(2));
        String rawValue = tokens.size() > 3 ? tokens.get(3) : "";
        Object value;
        if (type == Type.INTEGER) {
            value = Integer.parseInt(rawValue);
        } else if (type == Type.DOUBLE) {
            value = Double.parseDouble(rawValue);
        } else if (type == Type.BOOLEAN) {
            value = Boolean.parseBoolean(rawValue);
        } else if (type == Type.CHAR) {
            value = VmTextCodec.unquote(rawValue).charAt(0);
        } else {
            value = VmTextCodec.unquote(rawValue);
        }
        return new VmInstruction(VmOpCode.PUSH_CONST, value);
    }

    private static final class RoutineBuilder {
        private final String name;
        private final Type returnType;
        private final int parameterCount;
        private final int returnSlotIndex;
        private final List<VmSlot> slots = new ArrayList<>();
        private final List<VmInstruction> instructions = new ArrayList<>();

        private RoutineBuilder(List<String> tokens) {
            this.name = tokens.get(1);
            this.returnType = VmTypeCodec.decode(tokens.get(2));
            this.parameterCount = Integer.parseInt(tokens.get(3));
            this.returnSlotIndex = Integer.parseInt(tokens.get(4));
        }

        private VmRoutine build() {
            return new VmRoutine(name, returnType, parameterCount, returnSlotIndex, slots, instructions);
        }
    }
}
