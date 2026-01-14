package com.gisproject.location_score.repository;

import com.gisproject.location_score.entity.AdminMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<AdminMember, Long> {

    Optional<AdminMember> findByUsername(String username);
}