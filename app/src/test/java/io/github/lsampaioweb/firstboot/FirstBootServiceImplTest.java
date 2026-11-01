package io.github.lsampaioweb.firstboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import io.github.lsampaioweb.core.i18n.LogMessages;

@ExtendWith(MockitoExtension.class)
class FirstBootServiceImplTest {

  private static final String LOG_SCRIPT_SERVED = "log.first.boot.served";
  private static final String CLIENT = "10.1.9.1";
  private static final Map<String, List<String>> PARAMETERS =
      Map.of("interface", List.of("vmbr0"), "vlan-id", List.of("1110"));
  private static final String INITIAL = """
      auto lo
      iface lo inet loopback

      iface enp2lacp3 inet manual

      auto vmbr0
      iface vmbr0 inet static
              address 192.168.3.10/24
              netmask 255.255.255.0
              gateway 192.168.3.1
              bridge-ports enp2lacp3
              bridge-stp off
              bridge-fd 0

      iface enp2lacp0 inet manual

      iface enp2lacp1 inet manual

      iface enp2lacp2 inet manual
      """;
  private static final String CORRECT = """
      auto lo
      iface lo inet loopback

      iface enp2lacp3 inet manual

      auto vmbr0
      iface vmbr0 inet manual
              bridge-ports enp2lacp3
              bridge-stp off
              bridge-fd 0
              bridge-vlan-aware yes
              bridge-vids 1110

      auto vmbr0.1110
      iface vmbr0.1110 inet dhcp

      iface enp2lacp0 inet manual

      iface enp2lacp1 inet manual

      iface enp2lacp2 inet manual

      source /etc/network/interfaces.d/*
      """;

  @TempDir
  Path directory;

  @Mock
  private LogMessages logMessages;

  private FirstBootServiceImpl service;
  private Path script;
  private Path interfaces;

  @BeforeEach
  void setUp() throws IOException {
    service = new FirstBootServiceImpl(new FirstBootMapper(), logMessages);
    script = directory.resolve("first-boot.sh");
    Files.write(script, service.script(PARAMETERS, CLIENT, Locale.ENGLISH));
    interfaces = directory.resolve("interfaces");
  }

  @Test
  void returnsDeterministicBoundedScriptWithShebangAndValidBashSyntax() throws Exception {
    byte[] body = service.script(PARAMETERS, CLIENT, Locale.ENGLISH);
    assertThat(body).hasSizeLessThanOrEqualTo(1024 * 1024);
    assertThat(body).isEqualTo(service.script(PARAMETERS, CLIENT, Locale.forLanguageTag("pt-BR")));
    assertThat(new String(body, StandardCharsets.UTF_8))
        .startsWith("#!/bin/bash\n").contains("bridge='vmbr0'", "vlan_id='1110'")
        .doesNotContain("__BRIDGE__", "__VLAN_ID__", "ifreload", "ifup", "reboot", "curl");
    Process syntax = new ProcessBuilder("bash", "-n", script.toString()).start();
    assertThat(syntax.waitFor()).isZero();
  }

  @Test
  void logsEachServedScriptWithClientAndRequestedValuesOnly() {
    byte[] body = service.script(PARAMETERS, "10.1.9.1\nforged", Locale.ENGLISH);
    verify(logMessages).get(eq(Locale.ENGLISH), eq(LOG_SCRIPT_SERVED), any(Instant.class),
        eq("10.1.9.1_forged"), eq("vmbr0"), eq("1110"), eq(Integer.toString(body.length)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"en", "pt-BR"})
  void rendersLogIdentifiersWithoutGrouping(String languageTag) {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("i18n/messages");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    LogMessages messages = new LogMessages(source, "en");

    assertThat(messages.get(Locale.forLanguageTag(languageTag), LOG_SCRIPT_SERVED,
        Instant.parse("2026-11-01T14:00:00Z"), CLIENT, "vmbr0", "1110", "2048"))
        .contains("vlanId=1110", "bytes=2048");
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "4095", "-1", "3.0", " 1110", "01110", "1110;reboot", "999999999999"})
  void rejectsInvalidVlanValues(String vlan) {
    assertThatThrownBy(() -> service.script(
        Map.of("interface", List.of("vmbr0"), "vlan-id", List.of(vlan)), CLIENT, Locale.ENGLISH))
        .isInstanceOf(FirstBootException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"enp2lacp3", "vmbr0.1110", "vmbr0;reboot", "$(id)", "vmbr", "vmbr1234567890"})
  void rejectsUnsupportedBridgeNames(String bridge) {
    assertThatThrownBy(() -> service.script(
        Map.of("interface", List.of(bridge), "vlan-id", List.of("1110")), CLIENT, Locale.ENGLISH))
        .isInstanceOf(FirstBootException.class);
  }

  @Test
  void rejectsMissingDuplicateAndExtraParameters() {
    for (Map<String, List<String>> invalid : List.of(
        Map.<String, List<String>>of(),
        Map.of("interface", List.of("vmbr0")),
        Map.of("interface", List.of("vmbr0"), "vlan-id", List.of("1110", "3")),
        Map.of("interface", List.of("vmbr0", "vmbr1"), "vlan-id", List.of("1110")),
        Map.of("interface", List.of("vmbr0"), "vlan-id", List.of("1110"), "parent", List.of("eth0")))) {
      assertThatThrownBy(() -> service.script(invalid, CLIENT, Locale.ENGLISH)).isInstanceOf(FirstBootException.class);
    }
  }

  @Test
  void acceptsVlanBoundaryValues() {
    for (String vlan : List.of("1", "4094")) {
      assertThat(new String(service.script(
          Map.of("interface", List.of("vmbr0"), "vlan-id", List.of(vlan)), CLIENT, Locale.ENGLISH), StandardCharsets.UTF_8))
          .contains("vlan_id='" + vlan + "'");
    }
  }

  @Test
  void migratesInstallerStaticBridgeToDhcpVlanKeepingUnrelatedContent() throws Exception {
    String unrelated = "\n# untouched\niface vmbr9 inet manual\n        bridge-ports none\n";
    Files.writeString(interfaces, INITIAL + unrelated);
    Set<PosixFilePermission> permissions = Set.of(PosixFilePermission.OWNER_READ,
        PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ);
    Files.setPosixFilePermissions(interfaces, permissions);
    Object owner = Files.getOwner(interfaces);
    assertScriptSucceeds();
    assertThat(Files.readString(interfaces))
        .contains("iface vmbr0 inet manual", "bridge-ports enp2lacp3", "bridge-vlan-aware yes",
            "bridge-vids 1110", "auto vmbr0.1110\niface vmbr0.1110 inet dhcp", unrelated)
        .doesNotContain("192.168.3.", "netmask", "gateway");
    assertThat(Files.readString(backup())).isEqualTo(INITIAL + unrelated);
    assertThat(Files.getPosixFilePermissions(interfaces)).isEqualTo(permissions);
    assertThat(Files.getOwner(interfaces)).isEqualTo(owner);
    String firstResult = Files.readString(interfaces);
    assertScriptSucceeds();
    assertThat(Files.readString(interfaces)).isEqualTo(firstResult);
    assertThat(Files.readString(backup())).isEqualTo(INITIAL + unrelated);
    assertNoStagedFiles();
  }

  @Test
  void leavesActualAlreadyCorrectHomeLayoutByteForByteUnchanged() throws Exception {
    Files.writeString(interfaces, CORRECT);
    assertScriptSucceeds();
    assertThat(Files.readString(interfaces)).isEqualTo(CORRECT);
    assertThat(backup()).doesNotExist();
  }

  @Test
  void producesTheExpectedHomeLayoutFromTheInstallerFile() throws Exception {
    Files.writeString(interfaces, INITIAL);
    assertScriptSucceeds();
    assertThat(Files.readString(interfaces))
        .isEqualTo(CORRECT.replace("\nsource /etc/network/interfaces.d/*\n", ""));
  }

  @Test
  void failsWithoutChangesWhenTheBridgeIsMissing() throws Exception {
    String input = INITIAL.replace("vmbr0", "vmbr1");
    Files.writeString(interfaces, input);
    assertThat(runScript().exitCode()).isNotZero();
    assertThat(Files.readString(interfaces)).isEqualTo(input);
    assertThat(backup()).doesNotExist();
    assertNoStagedFiles();
  }

  private ScriptResult runScript() throws Exception {
    Path output = Files.createTempFile(directory, "script-output-", ".log");
    ProcessBuilder builder = new ProcessBuilder("bash", script.toString());
    builder.environment().put("PVE_INTERFACES_FILE", interfaces.toString());
    builder.redirectErrorStream(true).redirectOutput(output.toFile());
    Process process = builder.start();
    if (!process.waitFor(30, TimeUnit.SECONDS)) {
      process.destroyForcibly();
      throw new IllegalStateException("First-boot fixture exceeded 30 seconds");
    }

    return new ScriptResult(process.exitValue(), Files.readString(output));
  }

  private void assertScriptSucceeds() throws Exception {
    ScriptResult result = runScript();
    assertThat(result.exitCode()).as("script output: %s", result.output()).isZero();
  }

  private Path backup() {
    return directory.resolve("interfaces.before-first-boot-vlan");
  }

  private void assertNoStagedFiles() throws IOException {
    try (var paths = Files.list(directory)) {
      assertThat(paths.map(path -> path.getFileName().toString()).toList())
          .noneMatch(name -> name.startsWith("interfaces.vlan."));
    }
  }

  private record ScriptResult(int exitCode, String output) {
  }
}
