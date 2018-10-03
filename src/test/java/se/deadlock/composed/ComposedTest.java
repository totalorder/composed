package se.deadlock.composed;

import com.palantir.docker.compose.connection.waiting.HealthChecks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.HttpURLConnection;
import java.net.URL;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ComposedTest {
  @RegisterExtension
  static Composed nginx = Composed.builder()
      .projectName("composedtest")
      .dockerComposeFilePath("src/test/resources/docker-compose.yml")
      .serviceName("nginx-demo")
      .healtCheck(HealthChecks.toRespond2xxOverHttp(
          80, port -> "http://localhost:" + port.getExternalPort()))
      .build();

  @Test
  void returnsExternalPortAndContainerRespondsToHttp() throws Exception {
    final int externalPort = nginx.externalPort(80);
    assertThat(externalPort, is(greaterThan(0)));

    final URL url = new URL("http://localhost:" + externalPort);
    final HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");

    final int responseCode = connection.getResponseCode();
    assertThat(responseCode, is(200));
  }
}
