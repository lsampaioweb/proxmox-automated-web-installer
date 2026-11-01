package io.github.lsampaioweb.postinstall;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import io.github.lsampaioweb.core.i18n.LogMessages;

@Component
class PostInstallMapper {

  PostInstallEvent toEvent(PostInstallRequest request, Instant receivedAt) {
    PostInstallRequest.Product product = request.product();
    String uuid = null;
    if (request.dmi() != null && request.dmi().system() != null) {
      uuid = request.dmi().system().uuid();
    }
    List<PostInstallEvent.NetworkInterface> interfaces = request.networkInterfaces().stream()
        .map(item -> new PostInstallEvent.NetworkInterface(
            LogMessages.safeField(item.name()), LogMessages.safeField(item.mac())))
        .toList();

    return new PostInstallEvent(receivedAt, LogMessages.safeField(product.shortName()),
        LogMessages.safeField(product.fullname()), LogMessages.safeField(product.version()),
        LogMessages.safeField(request.fqdn()), LogMessages.safeField(uuid), interfaces);
  }

  PostInstallResponse toResponse(PostInstallEvent event) {
    return new PostInstallResponse(true, event.receivedAt());
  }
}
