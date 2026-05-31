package pascal.codegen;

import pascal.ast.Node;
import pascal.compiler.BackendArtifact;
import pascal.compiler.CompilerBackend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaBackend implements CompilerBackend {
    private final JavaCodeGenerator generator = new JavaCodeGenerator();

    @Override
    public String name() {
        return "java";
    }

    @Override
    public BackendArtifact emit(Node.ProgramNode program, Path targetDirectory) throws IOException {
        Files.createDirectories(targetDirectory);
        String javaSource = generator.generate(program);
        Path sourceFile = targetDirectory.resolve(className(program) + ".java");
        Files.writeString(sourceFile, javaSource, StandardCharsets.UTF_8);
        return new BackendArtifact(sourceFile, "Java source");
    }

    public String className(Node.ProgramNode program) {
        return generator.className(program.name);
    }
}
