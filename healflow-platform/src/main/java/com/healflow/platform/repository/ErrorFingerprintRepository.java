package com.healflow.platform.repository;

import com.healflow.platform.entity.ErrorFingerprintEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ErrorFingerprintRepository extends JpaRepository<ErrorFingerprintEntity, String> {}
