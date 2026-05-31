package pascal.vm;

import pascal.ast.Node;
import pascal.compiler.BackendArtifact;
import pascal.compiler.CompilerBackend;

import java.io.IOException;
import java.nio.file.Path;

public class VmBackend implements CompilerBackend {
    private final VmCompiler compiler = new VmCompiler();
    private final VmProgramWriter writer = new VmProgramWriter();

    @Override
    public String name() {
        return "vm";
    }

    public VmProgram compile(Node.ProgramNode program) {
        return compiler.compile(program);
    }

    @Override
    public BackendArtifact emit(Node.ProgramNode program, Path target) throws IOException {
        VmProgram compiled = compile(program);
        writer.write(compiled, target);
        return new BackendArtifact(target, "VM listing");
    }

    public String emitToString(Node.ProgramNode program) {
        return writer.writeToString(compile(program));
    }
}
