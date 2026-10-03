package io.github.lsampaioweb.answer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Extracts valid MAC addresses from an installer request.
 */
@Component
class AnswerMapperImpl implements AnswerMapper {

  /** Returns valid MAC addresses in the order the installer sent them. */
  @Override
  public List<String> normalizedMacs(AnswerRequest request) {
    List<NetworkInterfaceRequest> interfaces = request.networkInterfaces();
    if (interfaces == null || interfaces.isEmpty()) {
      return List.of();
    }

    List<String> macs = new ArrayList<>();
    for (NetworkInterfaceRequest networkInterface : interfaces) {
      if (networkInterface != null && networkInterface.mac() != null) {
        String mac = networkInterface.mac().trim();
        if (!mac.isEmpty()) {
          if (!AnswerProperties.MAC_ADDRESS_PATTERN.matcher(mac).matches()) {
            throw InvalidAnswerRequestException.forMalformedMacAddress();
          }
          macs.add(mac.toLowerCase(Locale.ROOT));
        }
      }
    }

    return List.copyOf(macs);
  }

}
