package io.github.yanziki.enterpriseai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EnterpriseAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseAiApplication.class, args);
    }
}
