package com.clearpath.repository;

import com.clearpath.model.Officer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OfficerRepository extends JpaRepository<Officer, Long> {
    Optional<Officer> findByOfficerId(String officerId);
    boolean existsByOfficerId(String officerId);
}
