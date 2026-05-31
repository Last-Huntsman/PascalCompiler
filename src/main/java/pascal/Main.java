package pascal;

import pascal.ast.Node;
import pascal.codegen.JavaBackend;
import pascal.compiler.CompilationPipeline;
import pascal.compiler.CompilationResult;
import pascal.compiler.ExternalProcessRunner;
import pascal.error.LexerException;
import pascal.error.ParseException;
import pascal.lexer.Token;
import pascal.vm.VmBackend;
import pascal.vm.VmProgram;
import pascal.vm.VmProgramLoader;
import pascal.vm.VmRuntime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Path DEFAULT_OUTPUT_DIR = Paths.get("build", "generated");
    private static final Path DEFAULT_VM_OUTPUT = Paths.get("build", "generated", "program.pvm");

    public static void main(String[] args) {
        Options options = Options.parse(args);
        if (options == null) {
            usage();
            System.exit(1);
            return;
        }

        String source;
        try {
            source = Files.readString(Paths.get(options.filename), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Cannot read file: " + options.filename);
            System.exit(1);
            return;
        }

        System.out.println("File: " + options.filename);
        System.out.println();

        CompilationPipeline pipeline = new CompilationPipeline();
        CompilationResult result;
        try {
            result = pipeline.compile(source, options.optimizationLevel);
        } catch (LexerException e) {
            System.out.println("--- Lexer ---");
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        } catch (ParseException e) {
            System.out.println("--- Lexer ---");
            System.out.println("OK.");
            System.out.println();
            System.out.println("--- Parser ---");
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        List<Token> tokens = result.tokens();
        System.out.println("--- Lexer ---");
        System.out.println("OK. Tokens: " + tokens.size());

        if (options.showTokens) {
            System.out.println();
            System.out.println("Tokens:");
            for (Token token : tokens) System.out.println("  " + token);
        }

        System.out.println();
        System.out.println("--- Parser ---");
        if (result.hasSyntaxDiagnostics()) {
            System.out.println("Syntax diagnostics (" + result.syntaxDiagnostics().size() + "):");
            for (String error : result.syntaxDiagnostics()) System.err.println("  " + error);
            System.exit(1);
            return;
        }
        System.out.println("OK. AST built.");

        System.out.println();
        System.out.println("--- Semantic analyzer ---");
        if (result.hasSemanticErrors()) {
            System.out.println("Semantic diagnostics (" + result.semanticResult().errors().size() + "):");
            for (String error : result.semanticResult().errors()) System.err.println("  " + error);
            System.exit(1);
            return;
        }
        System.out.println("OK. Semantic analysis completed.");

        if (options.optimizationLevel > 0) {
            System.out.println();
            System.out.println("--- Optimizer ---");
            System.out.println("OK. Local optimizations applied (level " + options.optimizationLevel + ").");
        }

        Node.ProgramNode program = result.program();
        if (options.printAst) {
            System.out.println();
            System.out.println("--- Typed AST ---");
            program.printTree();
        }

        if (options.emitJava || options.run) {
            generateAndMaybeRunJava(program, options);
        }

        if (options.emitVm || options.runVm) {
            generateAndMaybeRunVm(program, options);
        }
    }

    private static void generateAndMaybeRunJava(Node.ProgramNode program, Options options) {
        JavaBackend backend = new JavaBackend();
        ExternalProcessRunner runner = new ExternalProcessRunner();
        Path outputDir = options.outputDir == null ? DEFAULT_OUTPUT_DIR : options.outputDir;
        Path classesDir = outputDir.resolve("classes");

        try {
            Path sourceFile = backend.emit(program, outputDir).path();
            Files.createDirectories(classesDir);
            System.out.println();
            System.out.println("--- Code generation (Java) ---");
            System.out.println("Java source: " + sourceFile);

            if (!options.run) return;

            String className = backend.className(program);
            runner.run(List.of("javac", "-encoding", "UTF-8", "-d", classesDir.toString(), sourceFile.toString()), false);
            System.out.println();
            System.out.println("--- Program output (JVM) ---");
            runner.run(List.of("java", "-cp", classesDir.toString(), className), true);
        } catch (IOException e) {
            System.err.println("Cannot generate or run Java backend: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Java backend execution was interrupted: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void generateAndMaybeRunVm(Node.ProgramNode program, Options options) {
        VmBackend backend = new VmBackend();
        VmProgramLoader loader = new VmProgramLoader();
        VmRuntime runtime = new VmRuntime();

        try {
            String listing = backend.emitToString(program);
            Path outputFile = options.vmOutputFile == null ? DEFAULT_VM_OUTPUT : options.vmOutputFile;
            if (options.emitVm || options.runVm) {
                writeVmListing(outputFile, listing);
                System.out.println();
                System.out.println("--- Code generation (VM) ---");
                System.out.println("VM listing: " + outputFile);
            }
            if (!options.runVm) return;

            VmProgram loaded = loader.load(outputFile);
            System.out.println();
            System.out.println("--- Program output (VM) ---");
            runtime.execute(loaded, System.in, System.out);
        } catch (IOException e) {
            System.err.println("Cannot generate or run VM backend: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void writeVmListing(Path path, String listing) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, listing, StandardCharsets.UTF_8);
    }

    private static void usage() {
        System.out.println("Usage: pascal.Main [--tokens] [--no-ast] [--semantic] [--opt-level <0|1>] "
                + "[--emit-java <dir>] [--run] [--emit-vm <file>] [--run-vm] <file.pas>");
    }

    private static class Options {
        boolean showTokens;
        boolean printAst = true;
        boolean emitJava;
        boolean run;
        boolean emitVm;
        boolean runVm;
        int optimizationLevel = 1;
        Path outputDir;
        Path vmOutputFile;
        String filename;

        static Options parse(String[] args) {
            if (args.length == 0) return null;
            Options options = new Options();
            List<String> positional = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--tokens" -> options.showTokens = true;
                    case "--no-ast" -> options.printAst = false;
                    case "--semantic" -> { }
                    case "--run" -> options.run = true;
                    case "--run-vm" -> options.runVm = true;
                    case "--opt-level" -> {
                        if (i + 1 >= args.length) return null;
                        try {
                            options.optimizationLevel = Integer.parseInt(args[++i]);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                        if (options.optimizationLevel < 0 || options.optimizationLevel > 1) return null;
                    }
                    case "--emit-java" -> {
                        options.emitJava = true;
                        if (i + 1 >= args.length) return null;
                        options.outputDir = Paths.get(args[++i]);
                    }
                    case "--emit-vm" -> {
                        options.emitVm = true;
                        if (i + 1 >= args.length) return null;
                        options.vmOutputFile = Paths.get(args[++i]);
                    }
                    default -> positional.add(arg);
                }
            }
            if (positional.size() != 1) return null;
            options.filename = positional.get(0);
            return options;
        }
    }
}
