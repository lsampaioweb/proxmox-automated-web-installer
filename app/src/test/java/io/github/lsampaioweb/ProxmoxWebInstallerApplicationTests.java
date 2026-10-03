package io.github.lsampaioweb;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ProxmoxWebInstallerApplication.class, properties = "proxmox.root-password=test-root-password")
class ProxmoxWebInstallerApplicationTests {

  @Test
  void contextLoads() {
    // Reaching this test means the application context started successfully.
  }

}
