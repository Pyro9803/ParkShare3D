package com.parkshare.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findAllByRoleAndActive(UserRole role, boolean active, Pageable pageable);

    Page<User> findAllByRole(UserRole role, Pageable pageable);

    Page<User> findAllByActive(boolean active, Pageable pageable);
}
