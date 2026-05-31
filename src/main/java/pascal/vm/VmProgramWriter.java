package pascal.vm;

import pascal.semantic.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class VmProgramWriter {
    public String writeToString(VmProgram program) {
        StringBuilder out = new StringBuilder();
        line(out, "program " + program.programName());
        line(out, "entry " + program.entryRoutine());
        line(out, "");
        line(out, "globals " + program.globals().size());
        List<VmSlot> globals = program.globals();
        for (int i = 0; i < globals.size(); i++) {
            VmSlot slot = globals.get(i);
            line(out, "global " + i + " " + slot.name() + " " + VmTypeCodec.encode(slot.type()));
        }
        line(out, "");
        for (VmRoutine routine : program.routines()) {
            line(out, "routine " + routine.name() + " "
                    + VmTypeCodec.encode(routine.returnType()) + " "
                    + routine.parameterCount() + " "
                    + routine.returnSlotIndex());
            for (int i = 0; i < routine.slots().size(); i++) {
                VmSlot slot = routine.slots().get(i);
                line(out, "slot " + i + " " + slot.name() + " " + VmTypeCodec.encode(slot.type()));
            }
            for (VmInstruction instruction : routine.instructions()) {
                line(out, "instr " + renderInstruction(instruction));
            }
            line(out, "end");
            line(out, "");
        }
        return out.toString();
    }

    public void write(VmProgram program, Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, writeToString(program), StandardCharsets.UTF_8);
    }

    private String renderInstruction(VmInstruction instruction) {
        StringBuilder out = new StringBuilder(instruction.opcode().name());
        if (instruction.opcode() == VmOpCode.PUSH_CONST) {
            Object value = instruction.operands().get(0);
            Type type = scalarConstantType(value);
            out.append(' ').append(VmTypeCodec.encode(type));
            out.append(' ').append(renderConst(value, type));
            return out.toString();
        }
        for (Object operand : instruction.operands()) {
            out.append(' ');
            if (operand instanceof String text) {
                out.append(text);
            } else {
                out.append(operand);
            }
        }
        return out.toString();
    }

    private Type scalarConstantType(Object value) {
        if (value instanceof Integer) return Type.INTEGER;
        if (value instanceof Double) return Type.DOUBLE;
        if (value instanceof Boolean) return Type.BOOLEAN;
        if (value instanceof Character) return Type.CHAR;
        return Type.STRING;
    }

    private String renderConst(Object value, Type type) {
        if (type == Type.STRING || type == Type.CHAR) {
            return VmTextCodec.quote(String.valueOf(value));
        }
        return String.valueOf(value);
    }

    private void line(StringBuilder out, String line) {
        out.append(line).append(System.lineSeparator());
    }
}
