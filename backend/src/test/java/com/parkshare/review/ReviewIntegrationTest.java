package com.parkshare.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.VehicleType;
import com.parkshare.reservation.Reservation;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.reservation.ReservationStatus;
import com.parkshare.review.dto.CreateReviewRequest;
import com.parkshare.review.dto.ReviewResponse;
import com.parkshare.review.dto.SpotReviewsResponse;
import com.parkshare.shared.api.ApiResponse;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReviewIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
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

    private String driverToken;
    private UUID spotId;
    private UUID reservationId;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        vehicleRepository.deleteAll();
        parkingSpotRepository.deleteAll();
        parkingLotRepository.deleteAll();
        userRepository.deleteAll();

        User owner = userRepository.save(User.builder().email("owner@test.com").password("pwd").role(UserRole.OWNER).build());
        User driver = userRepository.save(User.builder().email("driver@test.com").password("pwd").role(UserRole.DRIVER).build());
        driverToken = jwtProvider.generateAccessToken(driver.getId(), driver.getEmail(), driver.getRole());

        ParkingLot lot = parkingLotRepository.save(ParkingLot.builder().ownerId(owner.getId()).name("Lot").address("Addr").floor(1).verified(true).active(true).build());
        ParkingSpot spot = parkingSpotRepository.save(ParkingSpot.builder().lotId(lot.getId()).code("A1").active(true).vehicleType(VehicleType.CAR).pricePerHour(new BigDecimal("50000")).build());
        spotId = spot.getId();

        Vehicle vehicle = vehicleRepository.save(Vehicle.builder().userId(driver.getId()).licensePlate("51A-12345").vehicleType(VehicleType.CAR).active(true).build());

        Reservation reservation = reservationRepository.save(Reservation.builder()
                .spotId(spotId).vehicleId(vehicle.getId()).driverId(driver.getId())
                .status(ReservationStatus.COMPLETED)
                .startTime(LocalDateTime.now().minusHours(2))
                .endTime(LocalDateTime.now().minusHours(1))
                .totalPrice(new BigDecimal("100000"))
                .build());
        reservationId = reservation.getId();
    }

    @Test
    void completeReviewFlow() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(driverToken);

        // 1. Post review
        CreateReviewRequest request = new CreateReviewRequest(reservationId, (short) 5, "Excellent!");
        ResponseEntity<ApiResponse<ReviewResponse>> postResponse = restTemplate.exchange(
                "/api/reviews",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(postResponse.getBody().data().rating()).isEqualTo(5);

        // 2. Get spot reviews
        ResponseEntity<ApiResponse<SpotReviewsResponse>> getResponse = restTemplate.exchange(
                "/api/parking-spots/" + spotId + "/reviews",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        SpotReviewsResponse data = getResponse.getBody().data();
        assertThat(data.averageRating()).isEqualTo(5.0);
        assertThat(data.reviews().content()).hasSize(1);
        assertThat(data.reviews().content().get(0).comment()).isEqualTo("Excellent!");
    }
}
