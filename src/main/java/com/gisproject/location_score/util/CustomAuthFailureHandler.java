package com.gisproject.location_score.util;

import com.gisproject.location_score.service.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;

@Component
@RequiredArgsConstructor
public class CustomAuthFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String username = request.getParameter("username"); // 폼의 input name과 일치해야 함
        String errorMessage = "아이디 또는 비밀번호가 일치하지 않습니다.";

        try {
            loginAttemptService.loginFailed(username);
        } catch (UsernameNotFoundException e) {
        }

        // 계정이 잠긴 경우 에러 메시지 변경
        if (exception instanceof LockedException) {
            errorMessage = "5회 이상 인증 실패로 계정이 잠겼습니다. 30분 뒤에 다시 시도하세요.";
        }

        // 한글 깨짐 방지 인코딩
        errorMessage = URLEncoder.encode(errorMessage, "UTF-8");
        setDefaultFailureUrl("/admin/login?error=true&exception=" + errorMessage);

        super.onAuthenticationFailure(request, response, exception);
    }
}