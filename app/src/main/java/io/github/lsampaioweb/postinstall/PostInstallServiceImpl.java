package io.github.lsampaioweb.postinstall;

import java.time.Instant;
import java.util.Locale;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.github.lsampaioweb.core.i18n.LogMessages;

@Slf4j
@Service
@RequiredArgsConstructor
class PostInstallServiceImpl implements PostInstallService {

  private static final String LOG_RECEIVED = "log.post.install.received";

  private final PostInstallMapper mapper;
  private final LogMessages logMessages;

  @Override
  public PostInstallResponse receive(PostInstallRequest request, Locale locale) {
    PostInstallEvent event = mapper.toEvent(request, Instant.now());
    log.info(logMessages.get(locale, LOG_RECEIVED, event.receivedAt(), event.product(),
        event.fullname(), event.version(), event.fqdn(), event.systemUuid(), event.interfaces()));

    return mapper.toResponse(event);
  }
}
