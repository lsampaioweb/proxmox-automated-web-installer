package io.github.lsampaioweb.firstboot;

import org.springframework.http.HttpStatus;

import io.github.lsampaioweb.core.exception.AppException;

class FirstBootException extends AppException {

  private static final String ERROR_PARAMETERS = "error.first.boot.parameters";
  private static final String ERROR_SCRIPT = "error.first.boot.script";

  FirstBootException(HttpStatus status) {
    super(status == HttpStatus.BAD_REQUEST ? ERROR_PARAMETERS : ERROR_SCRIPT,
        status == HttpStatus.BAD_REQUEST ? "FIRST_BOOT_PARAMETERS_INVALID" : "FIRST_BOOT_SCRIPT_UNAVAILABLE",
        null, status);
  }
}
