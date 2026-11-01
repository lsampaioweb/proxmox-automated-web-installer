package io.github.lsampaioweb.postinstall;

import java.util.Locale;

/** Receives installation-completion metadata without retaining the report. */
public interface PostInstallService {

  /** Logs the allowlisted summary and acknowledges receipt. */
  PostInstallResponse receive(PostInstallRequest request, Locale locale);
}
