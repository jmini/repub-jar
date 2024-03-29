/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package fr.jmini.utils.repubjar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import fr.jmini.utils.mvnutils.MavenArtifact;
import picocli.CommandLine;

class AppTest {

    @Test
    void mainOutput() throws Exception {
        runOutputTest("main-help-output.txt", "--help");
    }

    private void runOutputTest(String name, String... args) throws IOException {
        StringWriter sw = new StringWriter();
        CommandLine cmd = new CommandLine(new App());
        cmd.setOut(new PrintWriter(sw));

        assertThat(cmd.execute(args))
                .as("exit code")
                .isZero();
        String expectedOutput = readFromResource(name) + "\n";
        Path path = Paths.get("src/test/resources/" + name);
        Files.write(path, sw.toString()
                .getBytes(StandardCharsets.UTF_8));
        assertThat(sw)
                .as("output")
                .hasToString(expectedOutput);
    }

    @Test
    void testCreateModifiedContent() throws Exception {
        MavenArtifact input = new MavenArtifact("a", "b", "3.0.0");
        MavenArtifact output = new MavenArtifact("x", "y", "4.3.2");
        String content = ""
                + "<project>\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <groupId>a</groupId>\n"
                + "  <artifactId>b</artifactId>\n"
                + "  <version>3.0.0</version>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>a</groupId>\n"
                + "      <artifactId>a</artifactId>\n"
                + "      <version>3.0.0</version>\n"
                + "      <scope>runtime</scope>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>";
        String result = App.createModifiedContent(input, output, content);
        assertThat(result)
                .as("modified content")
                .hasToString(""
                        + "<project>\n"
                        + "  <modelVersion>4.0.0</modelVersion>\n"
                        + "  <groupId>x</groupId>\n"
                        + "  <artifactId>y</artifactId>\n"
                        + "  <version>4.3.2</version>\n"
                        + "  <dependencies>\n"
                        + "    <dependency>\n"
                        + "      <groupId>a</groupId>\n"
                        + "      <artifactId>a</artifactId>\n"
                        + "      <version>3.0.0</version>\n"
                        + "      <scope>runtime</scope>\n"
                        + "    </dependency>\n"
                        + "  </dependencies>\n"
                        + "</project>");
    }

    @Test
    void testCreateComment() throws Exception {
        MavenArtifact input = new MavenArtifact("com.ibm.icu", "icu4j", "64.2");
        String result = App.createComment(input,
                "https://download.eclipse.org/eclipse/updates/4.14/R-4.14-201912100610/plugins/com.ibm.icu_64.2.0.v20190507-1337.jar",
                "https://download.eclipse.org/eclipse/updates/4.14/R-4.14-201912100610/plugins/com.ibm.icu.source_64.2.0.v20190507-1337.jar");
        String expected = readFromResource("icu4j-comment.txt");
        assertThat(result)
                .as("modified content")
                .hasToString(expected);
    }

    @Test
    void testCreateCommentedContent() throws Exception {
        MavenArtifact input = new MavenArtifact("abc", "xyz", "3.3.1");
        String content = ""
                + "<project>\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <groupId>xxx</groupId>\n"
                + "  <artifactId>yyy</artifactId>\n"
                + "  <version>4.3.2</version>\n"
                + "  <dependencies>\n"
                + "    <dependency>\n"
                + "      <groupId>a</groupId>\n"
                + "      <artifactId>a</artifactId>\n"
                + "      <version>3.0.0</version>\n"
                + "      <scope>runtime</scope>\n"
                + "    </dependency>\n"
                + "  </dependencies>\n"
                + "</project>";
        String result = App.createCommentedContent(input, "https://some.com/some.jar", null, content);
        assertThat(result)
                .as("modified content")
                .hasToString(""
                        + "<project>\n"
                        + "<!--\n"
                        + "    Original jar location: https://some.com/some.jar\n"
                        + "    Original POM: abc:xyz:3.3.1\n"
                        + "    POM created by the tool: https://jmini.github.io/repub-jar\n"
                        + "-->\n"
                        + "  <modelVersion>4.0.0</modelVersion>\n"
                        + "  <groupId>xxx</groupId>\n"
                        + "  <artifactId>yyy</artifactId>\n"
                        + "  <version>4.3.2</version>\n"
                        + "  <dependencies>\n"
                        + "    <dependency>\n"
                        + "      <groupId>a</groupId>\n"
                        + "      <artifactId>a</artifactId>\n"
                        + "      <version>3.0.0</version>\n"
                        + "      <scope>runtime</scope>\n"
                        + "    </dependency>\n"
                        + "  </dependencies>\n"
                        + "</project>");
    }

    private static String readFromResource(String name) {
        InputStream inputStream = AppTest.class.getResourceAsStream("/" + name);
        return new BufferedReader(new InputStreamReader(inputStream))
                .lines()
                .collect(Collectors.joining("\n"));
    }
}
