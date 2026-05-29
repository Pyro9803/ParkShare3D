package com.parkshare.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.DayOfWeek;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.VehicleType;
import com.parkshare.reservation.dto.CheckInLogResponse;
import com.parkshare.reservation.dto.ReservationResponse;
import com.parkshare.shared.api.PagedResponse;
import com.parkshare.user.User;
import com.parkshare.user.UserRepository;
import com.parkshare.user.UserRole;
import com.parkshare.vehicle.Vehicle;
import com.parkshare.vehicle.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.parkshare.shared.api.ApiResponse;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReservationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingSpotRepository parkingSpotRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private com.parkshare.auth.jwt.JwtProvider jwtProvider;

    private User driver;
    private User owner;
    private ParkingLot lot;
    private ParkingSpot spot;
    private Vehicle vehicle;
    private Reservation reservation;
    private String driverToken;
    private String ownerToken;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        vehicleRepository.deleteAll();
        parkingSpotRepository.deleteAll();
        parkingLotRepository.deleteAll();
        userRepository.deleteAll();

        driver = userRepository.save(User.builder().email("driver@test.com").password("pwd").role(UserRole.DRIVER).build());
        owner = userRepository.save(User.builder().email("owner@test.com").password("pwd").role(UserRole.OWNER).build());

        driverToken = jwtProvider.generateAccessToken(driver.getId(), driver.getEmail(), driver.getRole());
        ownerToken = jwtProvider.generateAccessToken(owner.getId(), owner.getEmail(), owner.getRole());

        lot = parkingLotRepository.save(ParkingLot.builder().ownerId(owner.getId()).name("Lot").address("Addr").floor(1).verified(true).build());
        spot = parkingSpotRepository.save(ParkingSpot.builder().lotId(lot.getId()).code("A1").vehicleType(VehicleType.CAR).pricePerHour(new BigDecimal("50000")).build());
        vehicle = vehicleRepository.save(Vehicle.builder().userId(driver.getId()).licensePlate("51A-12345").vehicleType(VehicleType.CAR).build());

        reservation = reservationRepository.save(Reservation.builder()
                .spotId(spot.getId())
                .vehicleId(vehicle.getId())
                .driverId(driver.getId())
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now().plusMinutes(10))
                .endTime(LocalDateTime.now().plusMinutes(70))
                .totalPrice(new BigDecimal("50000.00"))
                .build());
    }

    @Test
    void cancel_happyPath_returns200() {
        // Change start time to 2 hours from now to be safely cancellable
        reservation.setStartTime(LocalDateTime.now().plusHours(2));
        reservationRepository.save(reservation);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(driverToken);
        ResponseEntity<ApiResponse<ReservationResponse>> response = restTemplate.exchange(
                "/api/reservations/" + reservation.getId() + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void cancel_tooLate_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(driverToken);
        ResponseEntity<ApiResponse<ReservationResponse>> response = restTemplate.exchange(
                "/api/reservations/" + reservation.getId() + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("CANCEL_WINDOW_EXPIRED");
    }

    @Test
    void checkIn_happyPath_returns200() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(driverToken);
        ResponseEntity<ApiResponse<ReservationResponse>> response = restTemplate.exchange(
                "/api/reservations/" + reservation.getId() + "/check-in",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().status()).isEqualTo(ReservationStatus.CHECKED_IN);
    }

    @Test
    void checkIn_outsideWindow_returns400() {
        reservation.setStartTime(LocalDateTime.now().plusHours(2));
        reservationRepository.save(reservation);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(driverToken);
        ResponseEntity<ApiResponse<ReservationResponse>> response = restTemplate.exchange(
                "/api/reservations/" + reservation.getId() + "/check-in",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().error().code()).isEqualTo("CHECKIN_WINDOW_EXPIRED");
    }

    @Test
    void checkOut_happyPath_createsLog_returns200() {
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservation.setCheckedInAt(LocalDateTime.now().minusHours(1));
        reservationRepository.save(reservation);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(driverToken);
        ResponseEntity<ApiResponse<CheckInLogResponse>> response = restTemplate.exchange(
                "/api/reservations/" + reservation.getId() + "/check-out",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().actualDurationMinutes()).isGreaterThanOrEqualTo(60);
    }

    @Test
    void getById_driver_returns200() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(driverToken);
        ResponseEntity<ApiResponse<ReservationResponse>> response = restTemplate.exchange(
                "/api/reservations/" + reservation.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().id()).isEqualTo(reservation.getId());
    }

    @Test
    void getById_owner_returns200() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerToken);
        ResponseEntity<ApiResponse<ReservationResponse>> response = restTemplate.exchange(
                "/api/reservations/" + reservation.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().id()).isEqualTo(reservation.getId());
    }

    @Test
    void getById_otherUser_returns403() {
        User other = userRepository.save(User.builder().email("other@test.com").password("pwd").role(UserRole.DRIVER).build());
        String otherToken = jwtProvider.generateAccessToken(other.getId(), other.getEmail(), other.getRole());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(otherToken);
        ResponseEntity<ApiResponse<ReservationResponse>> response = restTemplate.exchange(
                "/api/reservations/" + reservation.getId(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getByLot_owner_returns200() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(ownerToken);
        ResponseEntity<ApiResponse<PagedResponse<ReservationResponse>>> response = restTemplate.exchange(
                "/api/parking-lots/" + lot.getId() + "/reservations",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data().content()).hasSize(1);
    }
}
