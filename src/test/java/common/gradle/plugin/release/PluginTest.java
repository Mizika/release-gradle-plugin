package common.gradle.plugin.release;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@Ignore
public class PluginTest {

    @Rule
    public TemporaryFolder projectDir =  new TemporaryFolder(new File("build"));

    @Rule
    public TestName name = new TestName();

    private File resourcesDir = new File("src/test/resources");


    private void copyFile( String source,  String destination) throws Exception {
        Files.copy(resourcesDir.toPath().resolve(source), projectDir.getRoot().toPath().resolve(destination));
    }

    @Before
    public void init() throws Exception {
        copyFile("build.gradle", "build.gradle");
        copyFile("settings.gradle", "settings.gradle");

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(projectDir.getRoot());
        processBuilder.command("git", "init");
        processBuilder.start().waitFor();

        processBuilder.command("git", "add", ".");
        processBuilder.start().waitFor();

        processBuilder.command("git", "commit", "-m", "Initial commit");
        processBuilder.start().waitFor();

        processBuilder.command("git", "branch", "feature/CNM-1");
        processBuilder.start().waitFor();

        processBuilder.command("git", "checkout", "feature/CNM-1");
        processBuilder.start().waitFor();
    }

    @Test
    public void checkMix() throws Exception {
        var runner = GradleRunner.create()
                .withProjectDir(projectDir.getRoot())
                .withArguments(List.of("devSnapshot", "showVersion", "--stacktrace"))
                .withPluginClasspath()
                .forwardOutput()
                .withDebug(true);
        var result = runner.build();
        System.out.println(result);
    }
}
