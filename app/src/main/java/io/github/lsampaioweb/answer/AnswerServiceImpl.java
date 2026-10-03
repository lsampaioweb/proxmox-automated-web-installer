package io.github.lsampaioweb.answer;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.github.lsampaioweb.core.i18n.LogMessages;

/**
 * Finds answer files and records request audit details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class AnswerServiceImpl implements AnswerService {

  private static final String LOG_AUDIT = "log.answer.audit";
  private static final String LOG_MAC_ATTEMPT = "log.answer.mac.attempt";
  private static final String MAC_HIT = "HIT";
  private static final String MAC_MISS = "MISS";
  private static final String OUTCOME_SUCCESS = "SUCCESS";
  private static final String OUTCOME_BAD_REQUEST = "BAD_REQUEST";
  private static final String OUTCOME_NOT_FOUND = "NOT_FOUND";
  private static final String OUTCOME_UNREADABLE = "UNREADABLE";
  private static final String OUTCOME_INVALID_TOML = "INVALID_TOML";
  private static final String OUTCOME_INVALID_TEMPLATE = "INVALID_TEMPLATE";
  private static final String OUTCOME_INTERNAL_ERROR = "INTERNAL_ERROR";

  private final AnswerMapper answerMapper;
  private final AnswerRepository answerRepository;
  private final LogMessages logMessages;

  /** Returns the first matching answer file and records the request. */
  @Override
  public byte[] findAnswer(AnswerRequest request, String clientAddress, Locale locale) {
    Instant startedAt = Instant.now();
    String macMetadata = requestMacMetadata(request);
    String normalizedMac = null;
    String outcome = OUTCOME_SUCCESS;
    String errorCode = "";

    try {
      List<String> macs = answerMapper.normalizedMacs(request);
      if (macs.isEmpty()) {
        outcome = OUTCOME_BAD_REQUEST;
        throw new InvalidAnswerRequestException();
      }

      for (String mac : macs) {
        normalizedMac = mac;
        Optional<byte[]> content = readAnswer(mac, locale);
        if (content.isPresent()) {
          return content.get();
        }
      }

      // No requested MAC matched an answer file.
      throw new AnswerNotFoundException();
    } catch (InvalidAnswerRequestException ex) {
      outcome = OUTCOME_BAD_REQUEST;
      errorCode = ex.getErrorCode();
      throw ex;
    } catch (AnswerNotFoundException ex) {
      outcome = OUTCOME_NOT_FOUND;
      errorCode = ex.getErrorCode();
      throw ex;
    } catch (AnswerUnreadableException ex) {
      outcome = OUTCOME_UNREADABLE;
      errorCode = ex.getErrorCode();
      throw ex;
    } catch (InvalidAnswerTomlException ex) {
      outcome = OUTCOME_INVALID_TOML;
      errorCode = ex.getErrorCode();
      throw ex;
    } catch (InvalidAnswerTemplateException ex) {
      outcome = OUTCOME_INVALID_TEMPLATE;
      errorCode = ex.getErrorCode();
      throw ex;
    } catch (RuntimeException ex) {
      outcome = OUTCOME_INTERNAL_ERROR;
      errorCode = OUTCOME_INTERNAL_ERROR;
      throw ex;
    } finally {
      recordAudit(new AuditContext(startedAt, clientAddress, request, macMetadata, normalizedMac, outcome, errorCode,
          locale));
    }
  }

  private Optional<byte[]> readAnswer(String mac, Locale locale) {
    try {
      byte[] content = answerRepository.readByMac(mac);
      traceMacAttempt(locale, mac, MAC_HIT);

      return Optional.of(content);
    } catch (AnswerNotFoundException _) {
      traceMacAttempt(locale, mac, MAC_MISS);

      return Optional.empty();
    }
  }

  private void traceMacAttempt(Locale locale, String mac, String result) {
    if (log.isDebugEnabled()) {
      log.debug(logMessages.get(locale, LOG_MAC_ATTEMPT, safeLogField(mac), result));
    }
  }

  private void recordAudit(AuditContext context) {
    long latencyMs = Duration.between(context.startedAt(), Instant.now()).toMillis();
    String filename = context.normalizedMac() == null ? null
        : context.normalizedMac() + AnswerProperties.ANSWER_FILE_EXTENSION;
    AnswerRequest.Product product = context.request().product();
    AnswerRequest.Iso iso = context.request().iso();
    AnswerRequest.Dmi dmi = context.request().dmi();
    AnswerRequest.SystemInfo system = dmi == null ? null : dmi.system();
    AnswerRequest.Baseboard baseboard = dmi == null ? null : dmi.baseboard();
    AnswerRequest.Chassis chassis = dmi == null ? null : dmi.chassis();
    log.info(logMessages.get(
        context.locale(),
        LOG_AUDIT,
        context.startedAt().atOffset(ZoneOffset.UTC),
        safeLogField(context.clientAddress()),
        safeLogField(product == null ? null : product.product()),
        safeLogField(product == null ? null : product.fullname()),
        safeLogField(iso == null ? null : iso.release()),
        safeLogField(iso == null ? null : iso.isorelease()),
        safeLogField(context.request().schema() == null ? null : context.request().schema().version()),
        safeLogField(system == null ? null : system.serial()),
        safeLogField(system == null ? null : system.uuid()),
        safeLogField(system == null ? null : system.name()),
        safeLogField(baseboard == null ? null : baseboard.serial()),
        safeLogField(chassis == null ? null : chassis.serial()),
        safeLogField(context.macMetadata()),
        safeLogField(context.normalizedMac()),
        safeLogField(filename),
        context.outcome(),
        context.errorCode(),
        latencyMs));
  }

  private String requestMacMetadata(AnswerRequest request) {
    List<NetworkInterfaceRequest> interfaces = request.networkInterfaces();
    if (interfaces == null) {
      return "";
    }

    StringJoiner macs = new StringJoiner(",");
    for (NetworkInterfaceRequest networkInterface : interfaces) {
      if (networkInterface == null || networkInterface.mac() == null) {
        continue;
      }

      String mac = networkInterface.mac().trim();
      if (!mac.isEmpty()) {
        macs.add(mac.toLowerCase(Locale.ROOT));
      }
    }

    return macs.toString();
  }

  private String safeLogField(String value) {
    if (value == null) {
      return "";
    }

    // Keep request data from creating extra log lines.
    return value.replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]", "_");
  }

  private record AuditContext(Instant startedAt, String clientAddress, AnswerRequest request, String macMetadata,
      String normalizedMac, String outcome, String errorCode, Locale locale) {
  }

}
