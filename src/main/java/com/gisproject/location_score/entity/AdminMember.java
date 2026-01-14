package com.gisproject.location_score.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "admin_member")
public class AdminMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username; // ID

    @Column(nullable = false)
    private String password; // PW

    private String role; // "ROLE_ADMIN"

    @Column(name = "failed_attempts")
    private int failedAttempts = 0;

    @Column(name = "is_locked")
    private boolean isLocked = false;

    @Column(name = "lock_time")
    private Date lockTime;

    public AdminMember(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }
}