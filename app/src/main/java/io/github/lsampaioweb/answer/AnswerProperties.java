package io.github.lsampaioweb.answer;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Filesystem location of Proxmox answer files.
 *
 * @param directory directory that contains {@code <mac>.toml} files
 */
@ConfigurationProperties(prefix = "proxmox.answers")
public record AnswerProperties(Path directory) {
}
