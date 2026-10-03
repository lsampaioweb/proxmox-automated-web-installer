package io.github.lsampaioweb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import io.github.lsampaioweb.answer.ProxmoxProperties;

/**
 * Starts the Proxmox Web Installer.
 */
@SpringBootApplication
@EnableConfigurationProperties(ProxmoxProperties.class)
public class ProxmoxWebInstallerApplication {

  /**
   * Starts the Spring Boot application.
   *
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(ProxmoxWebInstallerApplication.class, args);
  }

}
