package pascal.vm;

import pascal.semantic.Type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VmRoutine {
    private final String name;
    private final Type returnType;
    private final int parameterCount;
    private final int returnSlotIndex;
    private final List<VmSlot> slots;
    private final List<VmInstruction> instructions;

    public VmRoutine(String name,
                     Type returnType,
                     int parameterCount,
                     int returnSlotIndex,
                     List<VmSlot> slots,
                     List<VmInstruction> instructions) {
        this.name = name;
        this.returnType = returnType;
        this.parameterCount = parameterCount;
        this.returnSlotIndex = returnSlotIndex;
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
        this.instructions = Collections.unmodifiableList(new ArrayList<>(instructions));
    }

    public String name() {
        return name;
    }

    public Type returnType() {
        return returnType;
    }

    public int parameterCount() {
        return parameterCount;
    }

    public int returnSlotIndex() {
        return returnSlotIndex;
    }

    public List<VmSlot> slots() {
        return slots;
    }

    public List<VmInstruction> instructions() {
        return instructions;
    }
}
