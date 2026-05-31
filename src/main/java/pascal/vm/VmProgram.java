package pascal.vm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class VmProgram {
    private final String programName;
    private final String entryRoutine;
    private final List<VmSlot> globals;
    private final Map<String, VmRoutine> routines;

    public VmProgram(String programName, String entryRoutine, List<VmSlot> globals, List<VmRoutine> routines) {
        this.programName = programName;
        this.entryRoutine = entryRoutine;
        this.globals = Collections.unmodifiableList(new ArrayList<>(globals));
        Map<String, VmRoutine> indexed = new LinkedHashMap<>();
        for (VmRoutine routine : routines) {
            indexed.put(normalize(routine.name()), routine);
        }
        this.routines = Collections.unmodifiableMap(indexed);
    }

    public String programName() {
        return programName;
    }

    public String entryRoutine() {
        return entryRoutine;
    }

    public List<VmSlot> globals() {
        return globals;
    }

    public List<VmRoutine> routines() {
        return new ArrayList<>(routines.values());
    }

    public VmRoutine routine(String name) {
        return routines.get(normalize(name));
    }

    private static String normalize(String name) {
        return name.toLowerCase();
    }
}
