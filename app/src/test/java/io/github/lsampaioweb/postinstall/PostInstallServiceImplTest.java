package io.github.lsampaioweb.postinstall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.json.JsonMapper;

import io.github.lsampaioweb.core.i18n.LogMessages;

@ExtendWith(MockitoExtension.class)
class PostInstallServiceImplTest {

  private static final String LOG_RECEIVED = "log.post.install.received";

  @Mock
  private LogMessages logMessages;

  @Test
  void logsOnlySanitizedAllowlistAndUsesServerReceiptTime() {
    PostInstallRequest request = JsonMapper.builder().build().readValue("""
        {"product":{"short":"pve","fullname":"Proxmox\\nVE","version":"9.2"},
         "fqdn":"pve-01\\u2028.lan.home","dmi":{"system":{"uuid":"node\\tUUID","serial":"SECRET-SERIAL"}},
         "network-interfaces":[{"name":"nic\\rname","mac":"60:be:b4:08:f7:69",
           "address":"SECRET-ADDRESS","udev-properties":{"token":"SECRET-TOKEN"}}],
         "machine-id":"SECRET-MACHINE","disks":["SECRET-DISK"],"ssh-public-host-keys":{"rsa":"SECRET-KEY"}}
        """, PostInstallRequest.class);
    when(logMessages.get(eq(Locale.ENGLISH), eq(LOG_RECEIVED), any(Object[].class)))
        .thenAnswer(call -> Arrays.toString(call.getArguments()));
    Logger logger = (Logger) LoggerFactory.getLogger(PostInstallServiceImpl.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      Instant before = Instant.now();
      PostInstallResponse response = new PostInstallServiceImpl(new PostInstallMapper(), logMessages)
          .receive(request, Locale.ENGLISH);
      assertThat(response.received()).isTrue();
      assertThat(response.receivedAt()).isBetween(before, Instant.now());
      assertThat(appender.list).hasSize(1);
      assertThat(appender.list.getFirst().getFormattedMessage())
          .contains("pve", "Proxmox_VE", "9.2", "pve-01_.lan.home", "node_UUID",
              "nic_name", "60:be:b4:08:f7:69", response.receivedAt().toString())
          .doesNotContain("SECRET", "\n", "\r", "\t", "\u2028");
      verify(logMessages).get(Locale.ENGLISH, LOG_RECEIVED, response.receivedAt(), "pve",
          "Proxmox_VE", "9.2", "pve-01_.lan.home", "node_UUID",
          new PostInstallMapper().toEvent(request, response.receivedAt()).interfaces());
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void mapsAbsentOptionalFieldsWithoutInventingMetadata() {
    PostInstallRequest request = JsonMapper.builder().build().readValue("""
        {"product":{"short":"pve"},"fqdn":"node.lan.home",
         "network-interfaces":[{"mac":"60:be:b4:08:f7:69"}]}
        """, PostInstallRequest.class);
    PostInstallEvent event = new PostInstallMapper().toEvent(request, Instant.now());
    assertThat(event.systemUuid()).isEmpty();
    assertThat(event.fullname()).isEmpty();
    assertThat(event.version()).isEmpty();
    assertThat(event.interfaces().getFirst().name()).isEmpty();
  }
}
