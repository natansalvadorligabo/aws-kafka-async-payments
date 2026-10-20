package dev.natan.payments.testsupport;

import java.nio.file.Path;

import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One LocalStack per test JVM, provisioned by the same init script docker-compose uses.
 *
 * <pre>{@code
 * @ServiceConnection
 * static LocalStackContainer localStack = PaymentsLocalStack.shared();
 * }</pre>
 */
public final class PaymentsLocalStack {

    // 4.14.0 is the last image that starts without a LOCALSTACK_AUTH_TOKEN (2026.x requires a license).
    private static final DockerImageName IMAGE = DockerImageName.parse("localstack/localstack:4.14.0");
    private static final Path INIT_SCRIPT = Path.of("..", "infra", "localstack", "init-aws.sh");

    private static final LocalStackContainer SHARED = new LocalStackContainer(IMAGE)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(INIT_SCRIPT, 0755),
                    "/etc/localstack/init/ready.d/init-aws.sh")
            .waitingFor(Wait.forLogMessage(".*payments infra ready.*", 1));

    private PaymentsLocalStack() {
    }

    public static synchronized LocalStackContainer shared() {
        if (!SHARED.isRunning()) {
            SHARED.start();
        }
        return SHARED;
    }
}
