package io.github.lsampaioweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import io.github.lsampaioweb.answer.AnswerProperties;

/**
 * Starts the Proxmox web installer application.
 */
@SpringBootApplication
@EnableConfigurationProperties(AnswerProperties.class)
public class ProxmoxWebInstallerApplication {

  /**
   * Boots the Spring application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(ProxmoxWebInstallerApplication.class, args);
  }

}
