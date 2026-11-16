package com.clearpath.repository;

import com.clearpath.model.Civilian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CivilianRepository extends JpaRepository<Civilian, Long> {
    Optional<Civilian> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
}
