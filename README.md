# Composed [![Build Status](https://travis-ci.com/totalorder/composed.svg?branch=master)](https://travis-ci.com/totalorder/composed)

Thin junit5 wrapper around https://github.com/palantir/docker-compose-rule

## Tests manage their docker dependencies
Test code that depends on a running webserver
```java
@RegisterExtension
static Composed nginx = Composed.builder()
  .projectName("composedtest")
  .dockerComposeFilePath("docker-compose.yml")
  .serviceName("nginx-demo")
  .healtCheck(HealthChecks.toRespond2xxOverHttp(
      80, port -> "http://localhost:" + port.getExternalPort()))
  .build();

@Test
void httpRequestIsSuccessful() throws Exception {
  int externalPort = nginx.externalPort(80);

  URL url = new URL("http://localhost:" + externalPort);
  HttpURLConnection connection = (HttpURLConnection)url.openConnection();
  connection.setRequestMethod("GET");

  final int responseCode = connection.getResponseCode();
  assertThat(responseCode, is(200));
}
``` 
docker-compose.yml
```yaml
version: '2.1'
services:
  nginx-demo:
    image: nginxdemos/hello
    ports:
      - 80
```