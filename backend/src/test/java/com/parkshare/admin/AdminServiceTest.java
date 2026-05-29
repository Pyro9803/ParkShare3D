package com.parkshare.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.parkshare.admin.dto.AdminUserResponse;
import com.parkshare.admin.dto.StatisticsResponse;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.reservation.Reservation;
import com.parkshare.reservation.ReservationMapper;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.reservation.ReservationStatus;
import com.parkshare.reservation.dto.ReservationResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.user.User;
import com.parkshare.user.UserRepository;
import com.parkshare.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    @Spy
    private ReservationMapper reservationMapper = Mappers.getMapper(ReservationMapper.class);

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, reservationRepository, parkingSpotRepository, reservationMapper);
    }

    @Test
    void getUsers_all_returnsPaged() {
        User user = User.builder().email("test@test.com").role(UserRole.DRIVER).active(true).build();
        when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));

        PagedResponse<AdminUserResponse> response = adminService.getUsers(null, null, 0, 20);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).email()).isEqualTo("test@test.com");
    }

    @Test
    void deactivateUser_success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("test@test.com").active(true).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AdminUserResponse response = adminService.deactivateUser(userId);

        assertThat(response.active()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void getReservations_withStatus_returnsPaged() {
        Reservation reservation = Reservation.builder().status(ReservationStatus.COMPLETED).build();
        when(reservationRepository.findAllByStatusOrderByCreatedAtDesc(eq(ReservationStatus.COMPLETED), any()))
                .thenReturn(new PageImpl<>(List.of(reservation)));

        PagedResponse<ReservationResponse> response = adminService.getReservations(ReservationStatus.COMPLETED, 0, 20);

        assertThat(response.content()).hasSize(1);
    }

    @Test
    void getStatistics_returnsAggregatedData() {
        when(userRepository.count()).thenReturn(10L);
        when(reservationRepository.count()).thenReturn(50L);
        when(reservationRepository.sumTotalPriceByStatusCompleted()).thenReturn(new BigDecimal("1000000.00"));
        when(parkingSpotRepository.countByActiveTrue()).thenReturn(5L);

        StatisticsResponse response = adminService.getStatistics();

        assertThat(response.totalUsers()).isEqualTo(10L);
        assertThat(response.totalRevenue()).isEqualByComparingTo("1000000.00");
    }
}
