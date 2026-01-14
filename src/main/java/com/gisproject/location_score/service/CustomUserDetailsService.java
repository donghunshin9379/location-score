package com.gisproject.location_score.service;

import com.gisproject.location_score.entity.AdminMember;
import com.gisproject.location_score.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final LoginAttemptService loginAttemptService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminMember admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("해당 관리자를 찾을 수 없습니다: " + username));

        // 로그인 시도 전 잠금 시간 만료 확인 및 자동 해제 로직 호출
        loginAttemptService.unlockWhenTimeExpired(admin);

        // 계정이 잠겨있는지 확인하여 Spring Security에 전달
        return User.builder()
                .username(admin.getUsername())
                .password(admin.getPassword())
                .roles(admin.getRole().replace("ROLE_", ""))
                .accountLocked(admin.isLocked()) // 이 값이 true면 LockedException 발생
                .build();
    }
}
