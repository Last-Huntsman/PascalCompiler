package pascal.compiler;

import pascal.ast.Node;

import java.io.IOException;
import java.nio.file.Path;

public interface CompilerBackend {
    String name();

    BackendArtifact emit(Node.ProgramNode program, Path target) throws IOException;
}
