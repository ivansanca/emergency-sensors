# emergency-sensors

Fuentes automáticas de incendios para el servicio
[emergency-management](https://github.com/ivansanca/emergency-management-back).
Consulta los focos de calor que detectan los satélites (NASA FIRMS), descarta
los que no son incendios y envía el resto al núcleo como eventos de incidente.

## Cómo funciona

```
NASA FIRMS ──> ingesta ──> sensor-satellite-hotspots ──> detector ──> emergency-events ──> emergency-management
                                                            │
                                                            └──> sensor-satellite-hotspots-dlt
```

1. **Ingesta.** Cada 15 minutos consulta la API de FIRMS (VIIRS NOAA-20 y
   NOAA-21) para un rectángulo alrededor de Cantabria y publica los focos
   nuevos, sin filtrar, en `sensor-satellite-hotspots`. Recuerda durante 48 h
   los focos ya enviados para no repetirlos en cada consulta.
2. **Detector.** Lee los focos y aplica los filtros, del más barato al más
   caro:
   - confianza baja (`l`);
   - observados hace más de 24 h;
   - cerca de una fuente de calor fija (las acerías de Santander y El
     Astillero);
   - fuera de los municipios de Cantabria.

   Los que pasan se envían a `emergency-events` como incendio forestal de
   gravedad 0 (IGP 0): un satélite no puede valorar la gravedad, así que el
   evento solo da la alerta y la gravedad la fijan los operadores.

El id del evento es el de la detección, así que si un foco se procesa dos veces
el núcleo ignora la copia.

## Tecnologías

- Java 21 y Spring Boot 4
- Kafka
- JTS para localizar cada foco en su municipio (recintos municipales del IGN)
- Testcontainers y WireMock en los tests

## Requisitos

- JDK 21
- Kafka en `localhost:9092`. Se levanta con el `docker-compose.yml` de
  emergency-management.
- emergency-management arrancado al menos una vez, porque es quien crea el topic
  `emergency-events`.
- Para la ingesta, una clave de la API de FIRMS (MAP_KEY), que se pide gratis
  en https://firms.modaps.eosdis.nasa.gov/api/map_key/.

## Puesta en marcha

Solo el detector (configuración por defecto):

```bash
./mvnw spring-boot:run
```

Con la ingesta de FIRMS activada:

```bash
export FIRMS_MAP_KEY=tu-clave
./mvnw spring-boot:run -Dspring-boot.run.arguments=--sensors.ingestion.enabled=true
```

La aplicación escucha en http://localhost:8082 (el 8080 lo usa
emergency-management y el 8081 Redpanda Console).

La ingesta está desactivada por defecto porque solo una instancia debe consultar
FIRMS. El detector, en cambio, puede ejecutarse en varias instancias: se reparten
las 6 particiones de `sensor-satellite-hotspots`.

### Reproducir un día pasado (perfil `replay`)

El perfil [`replay`](src/main/resources/application-replay.properties) vuelve a
pasar por toda la cadena los focos del 21 y 22 de marzo de 2026, un caso con
varios incendios forestales en Cantabria:

```bash
export FIRMS_MAP_KEY=tu-clave
./mvnw clean package
java -jar target/emergency-sensors-0.0.1-SNAPSHOT.jar --spring.profiles.active=replay
```

El perfil activa la ingesta, fija `sensors.firms.date=2026-03-21` con
`sensors.firms.day-range=2` y consulta las fuentes de calidad estándar (SP) de los
tres satélites VIIRS, porque FIRMS solo guarda los datos casi en tiempo real (NRT)
unos meses. También quita el límite de antigüedad del detector, que si no
descartaría todos los focos. Para otro día basta con cambiar la fecha, por ejemplo
`--sensors.firms.date=2026-08-15`, comprobando antes que las fuentes cubren ese día
(`/api/data_availability` de FIRMS).

## Configuración

Todo está en [`application.properties`](src/main/resources/application.properties).
Lo más habitual:

| Propiedad                               | Por defecto           | Descripción                                        |
|-----------------------------------------|-----------------------|----------------------------------------------------|
| `sensors.ingestion.enabled`             | `false`               | Activa la consulta a FIRMS                         |
| `sensors.detector.enabled`              | `true`                | Activa el detector                                 |
| `FIRMS_MAP_KEY` (variable de entorno)   | —                     | Clave de FIRMS; sin ella no se consulta            |
| `sensors.firms.datasets`                | NOAA-20 y NOAA-21 NRT | Satélites consultados                              |
| `sensors.firms.poll-interval`           | `PT15M`               | Intervalo entre consultas                          |
| `sensors.firms.date`                    | vacío                 | Primer día a consultar (`yyyy-MM-dd`); vacío = los últimos `day-range` días |
| `sensors.detector.accepted-confidences` | `n,h`                 | Confianzas VIIRS aceptadas                         |
| `sensors.detector.max-age`              | `PT24H`               | Antigüedad máxima de un foco (vacío = sin límite)  |
| `sensors.detector.static-heat-sources`  | 2 fuentes             | Fuentes de calor fijas y su radio                  |

## Topics de Kafka

| Topic                           | Uso                                                       |
|---------------------------------|-----------------------------------------------------------|
| `sensor-satellite-hotspots`     | Focos sin filtrar de FIRMS (6 particiones)                |
| `sensor-satellite-hotspots-dlt` | Focos que no se pueden procesar (ilegibles o rechazados)  |
| `emergency-events`              | Eventos de incidente para el núcleo (lo crea el núcleo)   |

Si Kafka no confirma un evento, el detector lo reintenta sin límite: un posible
incendio no se descarta por una caída del broker.

## Monitorización

Por `/actuator/metrics`:

- `sensors.firms.last.success`: hora de la última consulta correcta a cada
  satélite. Distingue un día sin incendios de unas consultas que llevan horas
  fallando.
- `sensors.detector.hotspots`: focos evaluados, por resultado (`accepted` o el
  motivo del descarte).

## Tests

```bash
./mvnw verify
```

Ejecuta los tests unitarios y los de integración, que levantan Kafka con
Testcontainers (requiere Docker). La API de FIRMS se simula con WireMock y CSV
reales de [`src/test/resources/firms`](src/test/resources/firms). El informe de
cobertura de JaCoCo queda en `target/site/jacoco/index.html`.
