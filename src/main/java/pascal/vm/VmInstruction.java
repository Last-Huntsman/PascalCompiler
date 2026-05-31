package pascal.vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VmInstruction {
    private final VmOpCode opcode;
    private final List<Object> operands;

    public VmInstruction(VmOpCode opcode, List<Object> operands) {
        this.opcode = opcode;
        this.operands = Collections.unmodifiableList(new ArrayList<>(operands));
    }

    public VmInstruction(VmOpCode opcode, Object... operands) {
        this.opcode = opcode;
        List<Object> items = new ArrayList<>(operands.length);
        Collections.addAll(items, operands);
        this.operands = Collections.unmodifiableList(items);
    }

    public VmOpCode opcode() {
        return opcode;
    }

    public List<Object> operands() {
        return operands;
    }
}
