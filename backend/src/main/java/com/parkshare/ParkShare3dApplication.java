package com.parkshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class ParkShare3dApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkShare3dApplication.class, args);
    }
}
