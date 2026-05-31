package pascal.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ExternalProcessRunner {
    public void run(List<String> command, boolean inheritIo) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (inheritIo) {
            builder.inheritIO();
        } else {
            builder.redirectErrorStream(true);
        }
        Process process = builder.start();
        String output = "";
        if (!inheritIo) {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        if (!output.isBlank()) {
            System.out.print(output);
        }
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
        }
    }
}
