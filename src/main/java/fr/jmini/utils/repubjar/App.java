package fr.jmini.utils.repubjar;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Function;

import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;

import fr.jmini.utils.mvnutils.Algorithm;
import fr.jmini.utils.mvnutils.Maven;
import fr.jmini.utils.mvnutils.MavenArtifact;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = App.APPLICATION_NAME, mixinStandardHelpOptions = true, versionProvider = fr.jmini.utils.repubjar.PropertiesVersionProvider.class)
public class App implements Runnable {

    public static final String APPLICATION_NAME = "repub-jar";

    @Spec
    private CommandSpec spec;

    @Option(names = "--input-group-id", required = true, description = "groupId of the input pom")
    private String inputGroupId;

    @Option(names = "--input-artifact-id", required = true, description = "artifactId of the input pom")
    private String inputArtifactId;

    @Option(names = "--input-version", required = true, description = "version of the input pom")
    private String inputVersion;

    @Option(names = "--input-jar", required = true, description = "url of the jar used as input")
    private String inputJarUrl;

    @Option(names = "--input-sources-jar", description = "url of the sources jar used as input")
    private String inputSourcesJarUrl;

    @Option(names = "--output-group-id", description = "groupId of the output artifacts, when not defined the groupId of the input pom is used")
    private String outputGroupId;

    @Option(names = "--output-artifact-id", description = "artifactId of the output artifacts, when not defined the artifactId of the input pom is used")
    private String outputArtifactId;

    @Option(names = "--output-version", description = "version of the output artifacts, when not defined the version of the input pom is used")
    private String outputVersion;

    @Option(names = "--working-dir", description = "Path of the working folder, when not defined a toporary folder is used")
    private String workingFolder;

    @Option(names = "--repository", defaultValue = "repository", description = "Folder path (relative to the working folder or absolute) of the maven repository where the artifacts are published. By default 'repository' inside the working folder is used")
    private String repository;

    @Override
    public void run() {
        try {
            executeCommand();
        } catch (IOException e) {
            throw new RuntimeException("Error excuting the command", e);
        }
    }

    private void executeCommand() throws IOException {
        Path root;
        if (workingFolder != null) {
            root = Paths.get(workingFolder);
        } else {
            root = Files.createTempDirectory("repub");
        }

        Path repo;
        if (repository.startsWith("/")) {
            repo = Paths.get(repository);
        } else {
            repo = root.resolve(repository);
        }

        MavenArtifact input = new MavenArtifact(inputGroupId, inputArtifactId, inputVersion);
        MavenArtifact output = new MavenArtifact(value(outputGroupId, inputGroupId), value(outputArtifactId, inputArtifactId), value(outputVersion, inputVersion));

        Path projectPom = prepareRootFolder(root);
        Path effectivePom = fetchEffectivePom(projectPom, input);

        Path modifiedPom = createModifiedPom(effectivePom, input, output);

        Path flattenedPom = createFlattenedPom(modifiedPom, output);

        Path commentedPom = addComment(flattenedPom, input, output, inputJarUrl, inputSourcesJarUrl);

        publishArtifacts(commentedPom, repo, output, inputJarUrl, inputSourcesJarUrl);
    }

    static Path prepareRootFolder(Path root) throws IOException {
        Files.createDirectories(root);
        Path pom = root.resolve("pom.xml");
        String pomContent = ""
                + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n"
                + "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "\n"
                + "    <groupId>g</groupId>\n"
                + "    <artifactId>a</artifactId>\n"
                + "    <version>1.0-SNAPSHOT</version>\n"
                + "    <packaging>pom</packaging>\n"
                + "</project>";
        Files.write(pom, pomContent.getBytes(StandardCharsets.UTF_8));
        return pom;
    }

    static Path fetchEffectivePom(Path projectPom, MavenArtifact input) {
        String groupId = input.getGroupId();
        String artifactId = input.getArtifactId();
        String version = input.getVersion();

        Path effective = projectPom.getParent()
                .resolve("effective_" + groupId + "__" + artifactId + "__" + version + ".xml");

        EmbeddedMaven.forProject(absolutePath(projectPom))
                .addProperty("output", absolutePath(effective))
                .addProperty("artifact", groupId + ":" + artifactId + ":" + version)
                .setGoals("org.apache.maven.plugins:maven-help-plugin:3.2.0:effective-pom")
                .build();

        return effective;
    }

    static Path createModifiedPom(Path effectivePom, MavenArtifact input, MavenArtifact output) {
        if (Objects.equals(input, output)) {
            return effectivePom;
        }

        String groupId = input.getGroupId();
        String artifactId = input.getArtifactId();
        String version = input.getVersion();

        Path modified = effectivePom.getParent()
                .resolve("effective_" + groupId + "__" + artifactId + "__" + version + ".xml");
        String content = readFile(effectivePom);
        String modifiedContent = createModifiedContent(input, output, content);
        writeFile(modified, modifiedContent);
        return modified;
    }

    static String createModifiedContent(MavenArtifact input, MavenArtifact output, String content) {
        String result = content;
        result = replaceFirstTag(result, "groupId", input, output, MavenArtifact::getGroupId);
        result = replaceFirstTag(result, "artifactId", input, output, MavenArtifact::getArtifactId);
        result = replaceFirstTag(result, "version", input, output, MavenArtifact::getVersion);
        return result;
    }

    private static String replaceFirstTag(String content, String tagName, MavenArtifact input, MavenArtifact output, Function<MavenArtifact, String> getter) {
        return content.replaceFirst("<" + tagName + ">" + getter.apply(input) + "</" + tagName + ">", "<" + tagName + ">" + getter.apply(output) + "</" + tagName + ">");
    }

    static Path createFlattenedPom(Path modifiedPom, MavenArtifact output) {
        String groupId = output.getGroupId();
        String artifactId = output.getArtifactId();
        String version = output.getVersion();

        Path flattened = modifiedPom.getParent()
                .resolve("flattened_" + groupId + "__" + artifactId + "__" + version + ".xml");
        String flattenedPomFilename = flattened.getFileName()
                .toString();
        EmbeddedMaven.forProject(absolutePath(modifiedPom))
                .addProperty("flattenedPomFilename", flattenedPomFilename)
                .setGoals("org.codehaus.mojo:flatten-maven-plugin:1.2.7:flatten")
                .build();
        return flattened;
    }

    static Path addComment(Path flattenedPom, MavenArtifact input, MavenArtifact output, String inputJar, String inputSourcesJar) {
        String groupId = output.getGroupId();
        String artifactId = output.getArtifactId();
        String version = output.getVersion();

        Path commented = flattenedPom.getParent()
                .resolve("commented_" + groupId + "__" + artifactId + "__" + version + ".xml");
        String content = readFile(flattenedPom);
        String commentedContent = createCommentedContent(input, inputJar, inputSourcesJar, content);
        writeFile(commented, commentedContent);
        return commented;
    }

    static String createCommentedContent(MavenArtifact input, String inputJar, String inputSourcesJar, String content) {
        String comment = createComment(input, inputJar, inputSourcesJar);
        return content.replaceFirst("  <modelVersion>", comment + "\n  <modelVersion>");
    }

    static String createComment(MavenArtifact input, String inputJar, String inputSourcesJar) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!--\n");
        sb.append("    Original jar location: " + inputJar + "\n");
        if (inputSourcesJar != null) {
            sb.append("    Original source jar location: " + inputSourcesJar + "\n");
        }
        sb.append("    Original POM: " + input.getGroupId() + ":" + input.getArtifactId() + ":" + input.getVersion() + "\n");
        sb.append("    POM created by the tool: https://jmini.github.io/repub-jar\n");
        sb.append("-->");
        return sb.toString();
    }

    static void publishArtifacts(Path commentedPom, Path repo, MavenArtifact output, String inputJar, String inputSourcesJar) throws IOException {
        String groupId = output.getGroupId();
        String artifactId = output.getArtifactId();
        String version = output.getVersion();

        Path jarFile = commentedPom.getParent()
                .resolve("jar_" + groupId + "__" + artifactId + "__" + version + ".jar");
        download(inputJar, jarFile);

        Path sourcesJar;
        if (inputSourcesJar != null) {
            sourcesJar = commentedPom.getParent()
                    .resolve("sourcesJar_" + groupId + "__" + artifactId + "__" + version + ".jar");
            download(inputSourcesJar, sourcesJar);
        } else {
            sourcesJar = null;
        }

        byte[] pomContent = Files.readAllBytes(commentedPom);
        Maven.writeFileToRepositoryWithArmoredFiles(repo, output, ".pom", pomContent, Algorithm.MD_5, Algorithm.SHA_1, Algorithm.SHA_256, Algorithm.SHA_512);

        byte[] jarContent = Files.readAllBytes(jarFile);
        Maven.writeFileToRepositoryWithArmoredFiles(repo, output, ".jar", jarContent, Algorithm.MD_5, Algorithm.SHA_1, Algorithm.SHA_256, Algorithm.SHA_512);

        if (sourcesJar != null) {
            MavenArtifact outputSources = new MavenArtifact(groupId, artifactId, version, "sources");
            byte[] sourcesJarContent = Files.readAllBytes(sourcesJar);
            Maven.writeFileToRepositoryWithArmoredFiles(repo, outputSources, ".jar", sourcesJarContent, Algorithm.MD_5, Algorithm.SHA_1, Algorithm.SHA_256, Algorithm.SHA_512);
        }
        System.out.println("Published to repository: " + repo.normalize());
    }

    private static String readFile(Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Can not read file " + file, e);
        }
    }

    private static void writeFile(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Can not write file " + file, e);
        }
    }

    private static String value(String value, String defaultWhenNull) {
        return value == null ? defaultWhenNull : value;
    }

    private static String absolutePath(Path root) {
        return root.toAbsolutePath()
                .toString();
    }

    private static void download(String url, Path file) throws IOException {
        System.out.println("Downloading file: " + url);
        InputStream in = new URL(url).openStream();
        Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("- saved to disk: " + file);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}