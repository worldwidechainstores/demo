package za.co.csnx.demo;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.flyway.FlywayConnectionDetails;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * Connection details, not a container bean: Spring stops a container bean when its context
	 * closes, so each cached context (up to 32) would need a server of its own.
	 */
	@Bean
	JdbcConnectionDetails postgresConnectionDetails() {
		PostgreSQLContainer<?> server = TestPostgres.server();
		return new JdbcConnectionDetails() {
			@Override
			public String getUsername() {
				return server.getUsername();
			}

			@Override
			public String getPassword() {
				return server.getPassword();
			}

			@Override
			public String getJdbcUrl() {
				return server.getJdbcUrl();
			}
		};
	}

	/**
	 * Keeps Flyway on its own connection to the server, as @ServiceConnection did, instead of
	 * borrowing from the context pool.
	 */
	@Bean
	FlywayConnectionDetails postgresFlywayConnectionDetails() {
		PostgreSQLContainer<?> server = TestPostgres.server();
		return new FlywayConnectionDetails() {
			@Override
			public String getUsername() {
				return server.getUsername();
			}

			@Override
			public String getPassword() {
				return server.getPassword();
			}

			@Override
			public String getJdbcUrl() {
				return server.getJdbcUrl();
			}
		};
	}

	/**
	 * The context cache keeps every pool alive, so the shared server would carry hundreds of idle
	 * connections. A test context keeps one idle and hands the rest back after ten seconds.
	 */
	@Bean
	static BeanPostProcessor smallIdleTestPools() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				if (bean instanceof HikariDataSource pool) {
					pool.setMinimumIdle(1);
					pool.setIdleTimeout(10_000);
				}
				return bean;
			}
		};
	}

}
