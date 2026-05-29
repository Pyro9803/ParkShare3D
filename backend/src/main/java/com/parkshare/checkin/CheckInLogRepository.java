package com.parkshare.checkin;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckInLogRepository extends JpaRepository<CheckInLog, UUID> {
}
