package com.gisproject.location_score.service;

import com.gisproject.location_score.entity.AdminMember;
import com.gisproject.location_score.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final AdminRepository adminRepository;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_DURATION = 30 * 60 * 1000; // 30분 (밀리초)

    @Transactional
    public void loginFailed(String username) {
        AdminMember admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자 없음"));

        admin.setFailedAttempts(admin.getFailedAttempts() + 1);

        if (admin.getFailedAttempts() >= MAX_ATTEMPTS) {
            admin.setLocked(true);
            admin.setLockTime(new Date());
        }
    }

    @Transactional
    public void loginSucceeded(String username) {
        adminRepository.findByUsername(username).ifPresent(admin -> {
            admin.setFailedAttempts(0);
            admin.setLocked(false);
            admin.setLockTime(null);
        });
    }

    // 잠금 시간이 지났는지 확인 (로그인 시도 시 호출)
    public boolean unlockWhenTimeExpired(AdminMember admin) {
        if (admin.getLockTime() == null) return false;

        long lockTime = admin.getLockTime().getTime();
        long currentTime = System.currentTimeMillis();

        if (currentTime - lockTime > LOCK_TIME_DURATION) {
            admin.setLocked(false);
            admin.setFailedAttempts(0);
            admin.setLockTime(null);
            adminRepository.save(admin);
            return true; // 잠금 풀림
        }
        return false; // 여전히 잠금 상태
    }
}
