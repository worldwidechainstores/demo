package za.co.csnx.demo;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The one postgres server a test JVM starts (platform docs/module-conventions.md, Backend, Tests).
 * Every Spring context shares it; Ryuk removes it when the JVM exits.
 */
public final class TestPostgres {

	private static final PostgreSQLContainer<?> SERVER = start();

	private TestPostgres() {
	}

	/**
	 * Every cached context keeps its own pool open against this one server, so the default 100
	 * connections run out about ten contexts in.
	 */
	@SuppressWarnings("resource")   // lives for the JVM; Ryuk removes it
	private static PostgreSQLContainer<?> start() {
		PostgreSQLContainer<?> server = new PostgreSQLContainer<>(DockerImageName.parse("postgres:latest"))
				.withCommand("postgres", "-c", "max_connections=400");
		server.start();
		return server;
	}

	public static PostgreSQLContainer<?> server() {
		return SERVER;
	}

}
