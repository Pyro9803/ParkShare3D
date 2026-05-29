package com.parkshare.admin;

import java.util.UUID;

import com.parkshare.admin.dto.AdminUserResponse;
import com.parkshare.admin.dto.StatisticsResponse;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.reservation.ReservationStatus;
import com.parkshare.reservation.dto.ReservationResponse;
import com.parkshare.reservation.ReservationMapper;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.user.User;
import com.parkshare.user.UserRepository;
import com.parkshare.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ParkingSpotRepository parkingSpotRepository;
    private final ReservationMapper reservationMapper;

    public AdminService(UserRepository userRepository,
                        ReservationRepository reservationRepository,
                        ParkingSpotRepository parkingSpotRepository,
                        ReservationMapper reservationMapper) {
        this.userRepository = userRepository;
        this.reservationRepository = reservationRepository;
        this.parkingSpotRepository = parkingSpotRepository;
        this.reservationMapper = reservationMapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<AdminUserResponse> getUsers(UserRole role, Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<User> userPage;

        if (role != null && active != null) {
            userPage = userRepository.findAllByRoleAndActive(role, active, pageable);
        } else if (role != null) {
            userPage = userRepository.findAllByRole(role, pageable);
        } else if (active != null) {
            userPage = userRepository.findAllByActive(active, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        return PagedResponse.from(userPage.map(this::toAdminUserResponse));
    }

    @Transactional
    public AdminUserResponse deactivateUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("USER_NOT_FOUND", "User not found"));
        user.setActive(false);
        return toAdminUserResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ReservationResponse> getReservations(ReservationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<com.parkshare.reservation.Reservation> reservationPage;

        if (status != null) {
            reservationPage = reservationRepository.findAllByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            reservationPage = reservationRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return PagedResponse.from(reservationPage.map(reservationMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics() {
        return new StatisticsResponse(
                userRepository.count(),
                reservationRepository.count(),
                reservationRepository.sumTotalPriceByStatusCompleted(),
                parkingSpotRepository.countByActiveTrue()
        );
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
