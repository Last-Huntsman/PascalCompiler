package pascal.vm;

import pascal.semantic.Type;

public class VmSlot {
    private final String name;
    private final Type type;

    public VmSlot(String name, Type type) {
        this.name = name;
        this.type = type;
    }

    public String name() {
        return name;
    }

    public Type type() {
        return type;
    }
}
