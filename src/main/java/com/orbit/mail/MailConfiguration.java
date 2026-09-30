package com.orbit.mail;

import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class MailConfiguration {
    @Bean
    JavaMailSender orbitMailSender(
            @Value("${orbit.mail.enabled:false}") boolean enabled,
            @Value("${orbit.mail.host:}") String host,
            @Value("${orbit.mail.port:587}") int port,
            @Value("${orbit.mail.username:}") String username,
            @Value("${orbit.mail.password:}") String password,
            @Value("${orbit.mail.starttls:true}") boolean startTls) {
        if (enabled && (host.isBlank() || port < 1 || port > 65535)) {
            throw new IllegalStateException("Enabled email requires a valid SMTP host and port.");
        }
        var sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding("UTF-8");
        sender.setUsername(username);
        sender.setPassword(password);
        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", Boolean.toString(!username.isBlank()));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(startTls));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(startTls));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "10000");
        properties.setProperty("mail.smtp.writetimeout", "10000");
        return sender;
    }
}
