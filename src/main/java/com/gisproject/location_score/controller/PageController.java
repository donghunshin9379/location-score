package com.gisproject.location_score.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String main() {
        return "index";
    }

    @GetMapping("/map")
    public String map() {
        return "map";
    }

    // 관리자-로그인 페이지
    @GetMapping("/admin/login")
    public String loginPage() {
        return "admin-login";
    }

    @GetMapping("/manager")
    public String manager() {
        return "admin";
    }


}