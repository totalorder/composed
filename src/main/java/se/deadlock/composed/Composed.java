package se.deadlock.composed;


import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.connection.Container;
import com.palantir.docker.compose.connection.waiting.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.Duration;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.util.*;

@Slf4j
@Data
public class Composed implements BeforeAllCallback {
  private static final Map<String, Project> projects = new HashMap<>();
  private static final Map<ServiceKey, Service> services = new HashMap<>();
  private static Set<ServiceKey> started = new HashSet<>();
  private final String projectName;
  private final ServiceKey serviceKey;
  private final Service service;
  private final Project project;

  private Composed(final String projectName,
                   final String dockerComposeFilePath,
                   final String serviceName,
                   final HealthCheck<Container> healthCheck) {
    try {
      this.projectName = projectName;
      this.serviceKey = new ServiceKey(projectName, serviceName);

      final Project existingRule = projects.get(projectName);
      if (existingRule != null && !existingRule.dockerComposeFilePath.equals(dockerComposeFilePath)) {
        throw new RuntimeException("Existing project " + projectName + " already has a dockerComposeFilePath ("
            + existingRule.dockerComposeFilePath + ") which cannot be different from new dockerComposeFilePath check ("
            + dockerComposeFilePath + ")");
      }

      if (existingRule == null) {
        final Project project = new Project(
            dockerComposeFilePath,
            DockerComposeRule.builder()
                .projectName(ProjectName.fromString(projectName))
                .file(dockerComposeFilePath)
                .build());
        projects.put(projectName, project);
      }

      project = projects.get(projectName);


      final Service existingService = services.get(serviceKey);
      if (existingService != null && existingService.healthCheck != healthCheck) {
        throw new RuntimeException("Existing service " + projectName + ":" + serviceName + " already has a healtcheck ("
            + existingService.healthCheck + ") which cannot be different from new health check (" + healthCheck + ")");
      }

      if (existingService == null) {
        final Container container = project.dockerComposeRule.containers().container(serviceKey.name);
        services.put(serviceKey, new Service(container, healthCheck));
      }

      service = services.get(serviceKey);
    } catch (final Exception e) {
      log.error("Composed initialization error", e);
      throw e;
    }
  }

  @Override
  public void beforeAll(final ExtensionContext context) throws Exception {
    if (started.add(serviceKey)) {
      startService();
    }
  }

  public int externalPort(final int internalPort) {
    final int externalPort = service.container.port(internalPort).getExternalPort();
    if (externalPort == 0) {
      throw new RuntimeException("No external port could be determined for internal port " + internalPort +
          " for " + projectName + ":" + serviceKey.name);
    }
    return externalPort;
  }

  private void startService() throws Exception {
    log.info("Starting service " + serviceKey.projectName + ":" + serviceKey.name);
    service.container.up();

    new ClusterWait(cluster ->
        Composed.containerHealthcheck().isHealthy(service.container), Duration.standardSeconds(15))
        .waitUntilReady(null);

    new ClusterWait(
        cluster -> service.healthCheck.isHealthy(service.container),
        Duration.standardSeconds(15)).waitUntilReady(null);
  }

  private static HealthCheck<Container> containerHealthcheck() {
    return container -> {
      try {
        return SuccessOrFailure.fromBoolean(
            container.state().isHealthy(),
            "Container is unhealthy: " + container.getContainerName());
      } catch (IOException | InterruptedException e) {
        return SuccessOrFailure.fromException(e);
      }
    };
  }


  public static class ComposedBuilder {
    private String projectName;
    private String dockerComposeFilePath;
    private String serviceName;
    private HealthCheck<Container> healthCheck;

    private ComposedBuilder() {}

    public ComposedBuilder projectName(final String projectName) {
      this.projectName = projectName;
      return this;
    }

    public ComposedBuilder dockerComposeFilePath(final String dockerComposeFilePath) {
      this.dockerComposeFilePath = dockerComposeFilePath;
      return this;
    }

    public ComposedBuilder serviceName(final String name) {
      this.serviceName = name;
      return this;
    }

    public ComposedBuilder healtCheck(final HealthCheck<Container> healthCheck) {
      this.healthCheck = healthCheck;
      return this;
    }

    public Composed build() {
      final HealthCheck<Container> healthCheck = Optional.ofNullable(this.healthCheck)
          .orElse(HealthChecks.toHaveAllPortsOpen());
      return new Composed(projectName, dockerComposeFilePath, serviceName, healthCheck);
    }
  }

  public static ComposedBuilder builder() {
    return new ComposedBuilder();
  }

  @Data
  static class ServiceKey {
    public final String projectName;
    public final String name;
  }

  @Data
  static class Service {
    public final Container container;
    public final HealthCheck<Container> healthCheck;
  }


  @Data
  static class Project {
    public final String dockerComposeFilePath;
    public final DockerComposeRule dockerComposeRule;
  }
}
