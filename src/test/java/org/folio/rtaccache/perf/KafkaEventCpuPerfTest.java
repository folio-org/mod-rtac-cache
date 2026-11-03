package org.folio.rtaccache.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.rtaccache.TestConstant.HOLDINGS_TOPIC;
import static org.folio.rtaccache.TestConstant.INSTANCE_TOPIC;
import static org.folio.rtaccache.TestConstant.ITEM_TOPIC;
import static org.folio.rtaccache.TestConstant.TEST_TENANT;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jdk.jfr.Recording;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.folio.rtaccache.BaseIntegrationTest;
import org.folio.rtaccache.TestUtil;
import org.folio.rtaccache.integration.KafkaMessageListener;
import org.folio.rtaccache.repository.RtacHoldingRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Measures the CPU cost of consuming inventory Kafka events against an EMPTY rtac_holding table, which is the
 * situation PTF ran Data Import / Bulk Edit in. Every event should therefore be a no-op for the cache.
 *
 * <p>Each scenario pre-loads a backlog onto the topic while its listener container is stopped, then starts the
 * container and measures until the consumer group's lag reaches zero (mirrors PTF's backlog drain). Reported per
 * message: process CPU, consumer-thread CPU, GC time, and loaded-class / metaspace growth. A JFR recording of the
 * first measured round of each scenario is written to {@code target/perf-jfr/} for hotspot analysis.
 *
 * <p>Run: {@code mvn -Pperf test -Dtest=KafkaEventCpuPerfTest}. Tunables (system properties):
 * perf.messages, perf.warmupMessages, perf.rounds, perf.instanceImageBytes, perf.holdingsImageBytes,
 * perf.itemImageBytes, perf.jfr.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@ActiveProfiles({"test-kafka"})
class KafkaEventCpuPerfTest extends BaseIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(KafkaEventCpuPerfTest.class);

  private static final String KAFKA_IMAGE_VERSION = "apache/kafka-native:4.2.0";
  private static final KafkaContainer kafkaContainer = new KafkaContainer(DockerImageName.parse(KAFKA_IMAGE_VERSION));

  private static final String ITEM_LISTENER_ID = "mod-rtac-cache-item-listener";
  private static final String UPDATE_INSTANCE_EVENT_PATH = "__files/kafka-events/update-instance-member-tenant-event.json";
  private static final String UPDATE_HOLDINGS_EVENT_PATH = "__files/kafka-events/update-holdings-event.json";
  private static final String CREATE_HOLDINGS_EVENT_PATH = "__files/kafka-events/create-holdings-event.json";
  private static final String UPDATE_ITEM_EVENT_PATH = "__files/kafka-events/update-item-event.json";
  private static final String CREATE_ITEM_EVENT_PATH = "__files/kafka-events/create-item-event.json";
  private static final String PADDING_LINE = "x".repeat(96);
  private static final Duration DRAIN_TIMEOUT = Duration.ofMinutes(30);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  static {
    kafkaContainer.start();
  }

  @Autowired
  private KafkaListenerEndpointRegistry listenerRegistry;
  @Autowired
  private RtacHoldingRepository holdingRepository;

  private KafkaProducer<String, String> producer;
  private Admin admin;

  @DynamicPropertySource
  static void kafkaProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    // Match production: application-test.yml turns on SQL echo and web debug logging, which would skew CPU.
    registry.add("spring.jpa.show-sql", () -> "false");
    registry.add("logging.level.org.springframework.web", () -> "info");
    // Production backs off from 2s; the failure scenarios measure CPU per retry, so a short interval keeps them quick.
    registry.add("folio.kafka.retry-interval-ms", () -> getIntProperty("perf.retryIntervalMs", 50));
    int concurrency = getIntProperty("perf.concurrency", 1);
    for (var listener : List.of("instance", "holdings-record", "item")) {
      registry.add("folio.kafka.listener." + listener + ".concurrency", () -> concurrency);
    }
  }

  @BeforeAll
  void setUp() {
    listenerRegistry.stop();

    var producerProps = new Properties();
    producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
    producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    producerProps.put(ProducerConfig.LINGER_MS_CONFIG, 20);
    producerProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 512 * 1024);
    producer = new KafkaProducer<>(producerProps);
    admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers()));
    int partitions = getIntProperty("perf.partitions", 1);
    if (partitions > 1) {
      try {
        admin.createPartitions(Map.of(
          INSTANCE_TOPIC, NewPartitions.increaseTo(partitions),
          HOLDINGS_TOPIC, NewPartitions.increaseTo(partitions),
          ITEM_TOPIC, NewPartitions.increaseTo(partitions))).all().get();
      } catch (Exception e) {
        throw new IllegalStateException("Failed to add partitions", e);
      }
    }

    withinTenant(TEST_TENANT, () -> assertThat(holdingRepository.count()).isZero());
    log.info("RTAC KAFKA PERF jvm: availableProcessors={}, maxHeapMb={}, args={}",
      Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024 * 1024),
      ManagementFactory.getRuntimeMXBean().getInputArguments());
  }

  @AfterAll
  void tearDown() {
    if (producer != null) {
      producer.close();
    }
    if (admin != null) {
      admin.close();
    }
  }

  @Test
  @Order(1)
  void perf_instanceUpdate_emptyCache() {
    var template = loadTemplate(UPDATE_INSTANCE_EVENT_PATH, getIntProperty("perf.instanceImageBytes", 6_000));
    runScenario("instance-update", INSTANCE_TOPIC, KafkaMessageListener.INSTANCE_LISTENER_ID, () -> {
      var id = UUID.randomUUID().toString();
      setIds(template, id, null);
      return new Message(id, OBJECT_MAPPER.writeValueAsString(template));
    });
  }

  @Test
  @Order(2)
  void perf_holdingsUpdate_emptyCache() {
    var template = loadTemplate(UPDATE_HOLDINGS_EVENT_PATH, getIntProperty("perf.holdingsImageBytes", 2_000));
    runScenario("holdings-update", HOLDINGS_TOPIC, KafkaMessageListener.HOLDINGS_RECORD_LISTENER_ID,
      () -> holdingsOrItemMessage(template, "instanceId"));
  }

  @Test
  @Order(3)
  void perf_holdingsCreate_emptyCache() {
    var template = loadTemplate(CREATE_HOLDINGS_EVENT_PATH, getIntProperty("perf.holdingsImageBytes", 2_000));
    runScenario("holdings-create", HOLDINGS_TOPIC, KafkaMessageListener.HOLDINGS_RECORD_LISTENER_ID,
      () -> holdingsOrItemMessage(template, "instanceId"));
  }

  @Test
  @Order(4)
  void perf_itemUpdate_emptyCache() {
    var template = loadTemplate(UPDATE_ITEM_EVENT_PATH, getIntProperty("perf.itemImageBytes", 2_000));
    runScenario("item-update", ITEM_TOPIC, ITEM_LISTENER_ID,
      () -> holdingsOrItemMessage(template, "holdingsRecordId"));
  }

  @Test
  @Order(5)
  void perf_itemCreate_emptyCache() {
    var template = loadTemplate(CREATE_ITEM_EVENT_PATH, getIntProperty("perf.itemImageBytes", 2_000));
    runScenario("item-create", ITEM_TOPIC, ITEM_LISTENER_ID,
      () -> holdingsOrItemMessage(template, "holdingsRecordId"));
  }

  /**
   * Theory: a record the handler cannot process is retried by {@code DefaultErrorHandler}, and every retry re-seeks
   * and re-polls (re-deserializes) the rest of the poll batch, multiplying CPU per offset. Every
   * {@code perf.badEvery}-th instance event carries an {@code instanceFormatIds} value the DTO cannot bind.
   */
  @Test
  @Order(6)
  void perf_instanceUpdate_retryableFailures() {
    var template = loadTemplate(UPDATE_INSTANCE_EVENT_PATH, getIntProperty("perf.instanceImageBytes", 6_000));
    int badEvery = getIntProperty("perf.badEvery", 10);
    var counter = new int[1];
    var feed = new Feed(INSTANCE_TOPIC, listener(KafkaMessageListener.INSTANCE_LISTENER_ID), () -> {
      var id = UUID.randomUUID().toString();
      setIds(template, id, null);
      var node = (ObjectNode) template.get("new");
      if (++counter[0] % badEvery == 0) {
        node.putObject("instanceFormatIds").put("unbindable", 1);
      } else {
        node.putArray("instanceFormatIds");
      }
      return new Message(id, OBJECT_MAPPER.writeValueAsString(template));
    });
    int messages = getIntProperty("perf.failureMessages", 2_000);
    log.info("RTAC KAFKA PERF MARK begin instance-retryable-failures");
    var result = drain("instance-retryable-1in" + badEvery, List.of(feed), messages, false);
    log.info("RTAC KAFKA PERF MARK end instance-retryable-failures");
    log.info("RTAC KAFKA PERF RESULT {}", result.format());
  }

  /**
   * Theory: records that fail with a non-retryable exception are each logged at ERROR with a full stack trace.
   * Every instance event carries an id that is not a UUID, so {@code UUID.fromString} throws.
   */
  @Test
  @Order(7)
  void perf_instanceUpdate_nonRetryableFailures() {
    var template = loadTemplate(UPDATE_INSTANCE_EVENT_PATH, getIntProperty("perf.instanceImageBytes", 6_000));
    var feed = new Feed(INSTANCE_TOPIC, listener(KafkaMessageListener.INSTANCE_LISTENER_ID), () -> {
      var id = "not-a-uuid-" + UUID.randomUUID();
      setIds(template, id, null);
      ((ObjectNode) template.get("new")).putArray("instanceFormatIds");
      return new Message(id, OBJECT_MAPPER.writeValueAsString(template));
    });
    int messages = getIntProperty("perf.failureMessages", 2_000);
    log.info("RTAC KAFKA PERF MARK begin instance-nonretryable-failures");
    var result = drain("instance-nonretryable-all", List.of(feed), messages, false);
    log.info("RTAC KAFKA PERF MARK end instance-nonretryable-failures");
    log.info("RTAC KAFKA PERF RESULT {}", result.format());
  }

  /**
   * Theory: GC churn when several listeners drain backlogs at once (PTF's DI Update + Bulk Edit). Run with a
   * production-sized heap ({@code JAVA_TOOL_OPTIONS=-Xmx768m}) and production-like partitions/concurrency.
   */
  @Test
  @Order(8)
  void perf_mixed_concurrentDrain_emptyCache() {
    var instanceTemplate = loadTemplate(UPDATE_INSTANCE_EVENT_PATH, getIntProperty("perf.instanceImageBytes", 6_000));
    var holdingsTemplate = loadTemplate(UPDATE_HOLDINGS_EVENT_PATH, getIntProperty("perf.holdingsImageBytes", 2_000));
    var itemTemplate = loadTemplate(UPDATE_ITEM_EVENT_PATH, getIntProperty("perf.itemImageBytes", 2_000));
    var feeds = List.of(
      new Feed(INSTANCE_TOPIC, listener(KafkaMessageListener.INSTANCE_LISTENER_ID), () -> {
        var id = UUID.randomUUID().toString();
        setIds(instanceTemplate, id, null);
        return new Message(id, OBJECT_MAPPER.writeValueAsString(instanceTemplate));
      }),
      new Feed(HOLDINGS_TOPIC, listener(KafkaMessageListener.HOLDINGS_RECORD_LISTENER_ID),
        () -> holdingsOrItemMessage(holdingsTemplate, "instanceId")),
      new Feed(ITEM_TOPIC, listener(ITEM_LISTENER_ID), () -> holdingsOrItemMessage(itemTemplate, "holdingsRecordId")));
    int messages = getIntProperty("perf.messages", 10_000);
    int rounds = getIntProperty("perf.rounds", 2);
    for (int round = 1; round <= rounds; round++) {
      var result = drain("mixed-round" + round, feeds, messages, false);
      log.info("RTAC KAFKA PERF RESULT {}", result.format());
    }
    withinTenant(TEST_TENANT, () -> assertThat(holdingRepository.count()).isZero());
  }

  private MessageListenerContainer listener(String listenerId) {
    var container = listenerRegistry.getListenerContainer(listenerId);
    assertThat(container).as("listener container %s", listenerId).isNotNull();
    return container;
  }

  private void runScenario(String name, String topic, String listenerId, Supplier<Message> messageFactory) {
    var feed = new Feed(topic, listener(listenerId), messageFactory);
    var sample = messageFactory.get();
    log.info("RTAC KAFKA PERF [{}] messageBytes={}", name, sample.value().length());

    int warmup = getIntProperty("perf.warmupMessages", 2_000);
    int messages = getIntProperty("perf.messages", 10_000);
    int rounds = getIntProperty("perf.rounds", 2);

    drain(name + "-warmup", List.of(feed), warmup, false);
    for (int round = 1; round <= rounds; round++) {
      boolean jfr = round == 1 && getBooleanProperty("perf.jfr", true);
      var result = drain(name + "-round" + round, List.of(feed), messages, jfr);
      log.info("RTAC KAFKA PERF RESULT {}", result.format());
    }

    // Every event must have been a cache no-op; anything else means the scenario is not the one PTF ran.
    withinTenant(TEST_TENANT, () -> assertThat(holdingRepository.count()).isZero());
  }

  /** Pre-loads {@code countPerFeed} messages onto every feed's topic, then drains them all concurrently. */
  private Result drain(String label, List<Feed> feeds, int countPerFeed, boolean jfr) {
    for (var feed : feeds) {
      for (int i = 0; i < countPerFeed; i++) {
        var message = feed.messageFactory().get();
        producer.send(new ProducerRecord<>(feed.topic(), message.key(), message.value()));
      }
    }
    producer.flush();

    var before = Snapshot.take();
    Recording recording = jfr ? startRecording() : null;
    long startNanos = System.nanoTime();
    feeds.forEach(feed -> feed.container().start());
    Snapshot after;
    long wallNanos;
    try {
      feeds.forEach(feed -> awaitZeroLag(feed.topic(), feed.container().getGroupId()));
      wallNanos = System.nanoTime() - startNanos;
      // Consumer threads exit on stop, so their CPU has to be read while the container is still running.
      after = Snapshot.take();
    } finally {
      feeds.forEach(feed -> feed.container().stop());
    }
    if (recording != null) {
      stopRecording(recording, label);
    }
    return Result.of(label, countPerFeed * feeds.size(), wallNanos, before, after);
  }

  private void awaitZeroLag(String topic, String groupId) {
    long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline) {
      try {
        var partitions = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic)
          .partitions().stream()
          .map(p -> new TopicPartition(topic, p.partition()))
          .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
        var endOffsets = admin.listOffsets(partitions).all().get();
        Map<TopicPartition, OffsetAndMetadata> committed =
          admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
        long lag = 0;
        for (var entry : endOffsets.entrySet()) {
          var offset = committed.get(entry.getKey());
          lag += entry.getValue().offset() - (offset == null ? 0 : offset.offset());
        }
        if (lag == 0) {
          return;
        }
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      } catch (Exception e) {
        throw new IllegalStateException("Failed to read consumer lag for " + groupId, e);
      }
    }
    throw new IllegalStateException("Consumer group " + groupId + " did not drain within " + DRAIN_TIMEOUT);
  }

  private static Recording startRecording() {
    try {
      var recording = new Recording(jdk.jfr.Configuration.getConfiguration("profile"));
      // The profile default (10-20ms) yields too few samples on a consumer thread that is mostly waiting on I/O.
      recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
      recording.enable("jdk.ClassDefine").withStackTrace();
      recording.enable("jdk.ClassLoad").withStackTrace();
      recording.start();
      return recording;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to start JFR recording", e);
    }
  }

  private static void stopRecording(Recording recording, String label) {
    try {
      recording.stop();
      var dir = Path.of("target", "perf-jfr");
      Files.createDirectories(dir);
      var file = dir.resolve(label + ".jfr");
      recording.dump(file);
      log.info("RTAC KAFKA PERF [{}] jfr={}", label, file.toAbsolutePath());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to write JFR recording", e);
    } finally {
      recording.close();
    }
  }

  private static ObjectNode loadTemplate(String path, int imageBytes) {
    var template = (ObjectNode) OBJECT_MAPPER.readTree(TestUtil.readFileContentFromResources(path));
    template.put("tenant", TEST_TENANT);
    pad(template.get("old"), imageBytes);
    pad(template.get("new"), imageBytes);
    return template;
  }

  /**
   * Grows an event image to roughly {@code imageBytes} with string array entries. The typed DTOs do not bind
   * {@code administrativeNotes}, so this adds JSON parsing work without changing handler behaviour.
   */
  private static void pad(JsonNode image, int imageBytes) {
    if (!(image instanceof ObjectNode objectNode)) {
      return;
    }
    var notes = objectNode.putArray("administrativeNotes");
    int size = OBJECT_MAPPER.writeValueAsString(objectNode).length();
    while (size < imageBytes) {
      notes.add(PADDING_LINE);
      size += PADDING_LINE.length() + 3;
    }
  }

  private static Message holdingsOrItemMessage(ObjectNode template, String parentField) {
    var id = UUID.randomUUID().toString();
    setIds(template, id, parentField);
    return new Message(id, OBJECT_MAPPER.writeValueAsString(template));
  }

  private static void setIds(ObjectNode template, String id, String parentField) {
    var parentId = UUID.randomUUID().toString();
    template.put("eventId", UUID.randomUUID().toString());
    for (var image : new String[] {"old", "new"}) {
      if (template.get(image) instanceof ObjectNode node) {
        node.put("id", id);
        if (parentField != null && node.has(parentField)) {
          node.put(parentField, parentId);
        }
      }
    }
  }

  private static int getIntProperty(String propertyName, int defaultValue) {
    String value = System.getProperty(propertyName);
    return (value == null || value.isBlank()) ? defaultValue : Integer.parseInt(value);
  }

  private static boolean getBooleanProperty(String propertyName, boolean defaultValue) {
    String value = System.getProperty(propertyName);
    return (value == null || value.isBlank()) ? defaultValue : Boolean.parseBoolean(value);
  }

  private record Message(String key, String value) { }

  private record Feed(String topic, MessageListenerContainer container, Supplier<Message> messageFactory) { }

  private record Snapshot(long processCpuNanos, Map<String, Long> threadCpuNanos, long gcMillis, long gcCount,
                          long loadedClasses, long totalLoadedClasses, long metaspaceBytes) {

    static Snapshot take() {
      var os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
      ThreadMXBean threads = ManagementFactory.getThreadMXBean();
      Map<String, Long> threadCpu = new HashMap<>();
      for (var info : threads.getThreadInfo(threads.getAllThreadIds())) {
        if (info == null) {
          continue;
        }
        long cpu = threads.getThreadCpuTime(info.getThreadId());
        if (cpu > 0) {
          threadCpu.merge(threadGroupName(info.getThreadName()), cpu, Long::sum);
        }
      }
      long gcMillis = 0;
      long gcCount = 0;
      for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
        gcMillis += Math.max(0, gc.getCollectionTime());
        gcCount += Math.max(0, gc.getCollectionCount());
      }
      ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
      long metaspace = ManagementFactory.getMemoryPoolMXBeans().stream()
        .filter(pool -> "Metaspace".equals(pool.getName()))
        .mapToLong(pool -> pool.getUsage().getUsed())
        .sum();
      return new Snapshot(os.getProcessCpuTime(), threadCpu, gcMillis, gcCount, classes.getLoadedClassCount(),
        classes.getTotalLoadedClassCount(), metaspace);
    }

    /** Collapses per-instance thread names (pool-3-thread-7, ...-0-C-1) so CPU can be summed by role. */
    private static String threadGroupName(String name) {
      return name.replaceAll("\\d+", "#");
    }
  }

  private record Result(String label, int messages, long wallNanos, long processCpuNanos, String topThreads,
                        long consumerCpuNanos, long nonJavaThreadCpuNanos, long gcMillis, long gcCount,
                        long classesLoaded, long liveClassDelta, long metaspaceDeltaBytes) {

    static Result of(String label, int messages, long wallNanos, Snapshot before, Snapshot after) {
      Map<String, Long> threadDelta = new HashMap<>();
      after.threadCpuNanos().forEach((thread, cpu) ->
        threadDelta.put(thread, cpu - before.threadCpuNanos().getOrDefault(thread, 0L)));
      long consumerCpu = threadDelta.entrySet().stream()
        .filter(e -> e.getKey().contains("-C-"))
        .mapToLong(Map.Entry::getValue)
        .sum();
      long javaThreadsCpu = threadDelta.values().stream().mapToLong(Long::longValue).sum();
      long processCpu = after.processCpuNanos() - before.processCpuNanos();
      String top = threadDelta.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
        .limit(8)
        .map(e -> e.getKey() + "=" + e.getValue() / 1_000_000 + "ms")
        .collect(Collectors.joining(", "));
      return new Result(label, messages, wallNanos, processCpu, top,
        consumerCpu, processCpu - javaThreadsCpu, after.gcMillis() - before.gcMillis(),
        after.gcCount() - before.gcCount(),
        after.totalLoadedClasses() - before.totalLoadedClasses(), after.loadedClasses() - before.loadedClasses(),
        after.metaspaceBytes() - before.metaspaceBytes());
    }

    String format() {
      double wallSec = wallNanos / 1e9;
      return String.format(
        "%s messages=%d wallSec=%.1f msgPerSec=%.0f processCpuMsPerMsg=%.3f consumerThreadCpuMsPerMsg=%.3f "
          + "nonJavaThreadCpuMsPerMsg=%.3f "
          + "gcMs=%d gcCount=%d classesLoaded=%d classesLoadedPer1kMsg=%.1f liveClassDelta=%d metaspaceDeltaKb=%d "
          + "topThreads=[%s]",
        label, messages, wallSec, messages / wallSec, processCpuNanos / 1e6 / messages,
        consumerCpuNanos / 1e6 / messages, nonJavaThreadCpuNanos / 1e6 / messages, gcMillis, gcCount, classesLoaded,
        classesLoaded * 1000.0 / messages,
        liveClassDelta, metaspaceDeltaBytes / 1024, topThreads);
    }
  }
}
