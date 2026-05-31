package pascal.compiler;

import java.nio.file.Path;

public class BackendArtifact {
    private final Path path;
    private final String description;

    public BackendArtifact(Path path, String description) {
        this.path = path;
        this.description = description;
    }

    public Path path() {
        return path;
    }

    public String description() {
        return description;
    }
}
