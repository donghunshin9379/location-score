package com.gisproject.location_score.util;

import com.gisproject.location_score.entity.AdminMember;
import com.gisproject.location_score.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private static final Logger logger = LoggerFactory.getLogger(AdminInitializer.class);

    // application.properties 환경변수 값
    @Value("${security.admin.username}")
    private String adminUsername;

    @Value("${security.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) throws Exception {
        // DB에 해당 ID가 없을 때만 생성 (운영 서버 재시작 시 데이터 유지)
        if (adminRepository.findByUsername(adminUsername).isEmpty()) {
            AdminMember admin = new AdminMember();
            admin.setUsername(adminUsername);
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRole("ROLE_ADMIN");

            adminRepository.save(admin);
            logger.info(">>> 운영 모드 관리자 계정 생성됨: {}", adminUsername);
        }
    }
}