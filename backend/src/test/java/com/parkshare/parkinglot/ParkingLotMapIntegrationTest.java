package com.parkshare.parkinglot;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.parkshare.parkinglot.dto.ParkingLotMapResponse;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.SpotStatus;
import com.parkshare.parkingspot.VehicleType;
import com.parkshare.reservation.Reservation;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.reservation.ReservationStatus;
import com.parkshare.reservation.dto.CreateReservationRequest;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
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
class ParkingLotMapIntegrationTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        public Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneId.systemDefault());
        }
    }

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
    private StringRedisTemplate redisTemplate;

    @Autowired
    private com.parkshare.auth.jwt.JwtProvider jwtProvider;

    @Autowired
    private Clock clock;

    private String driverToken;
    private UUID lotId;
    private UUID spotAId, spotBId, spotCId, spotDId;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAll();
        vehicleRepository.deleteAll();
        parkingSpotRepository.deleteAll();
        parkingLotRepository.deleteAll();
        userRepository.deleteAll();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.flushAll();
            return null;
        });

        User owner = userRepository.save(User.builder().email("owner@test.com").password("pwd").role(UserRole.OWNER).build());
        User driver = userRepository.save(User.builder().email("driver@test.com").password("pwd").role(UserRole.DRIVER).build());
        driverToken = jwtProvider.generateAccessToken(driver.getId(), driver.getEmail(), driver.getRole());

        ParkingLot lot = parkingLotRepository.save(ParkingLot.builder().ownerId(owner.getId()).name("Verified Lot").address("Addr").floor(1).verified(true).active(true).build());
        lotId = lot.getId();

        ParkingSpot spotA = parkingSpotRepository.save(ParkingSpot.builder().lotId(lotId).code("A").active(true).vehicleType(VehicleType.CAR).pricePerHour(new BigDecimal("50000")).build());
        ParkingSpot spotB = parkingSpotRepository.save(ParkingSpot.builder().lotId(lotId).code("B").active(true).vehicleType(VehicleType.CAR).pricePerHour(new BigDecimal("50000")).build());
        ParkingSpot spotC = parkingSpotRepository.save(ParkingSpot.builder().lotId(lotId).code("C").active(true).vehicleType(VehicleType.CAR).pricePerHour(new BigDecimal("50000")).build());
        ParkingSpot spotD = parkingSpotRepository.save(ParkingSpot.builder().lotId(lotId).code("D").active(false).vehicleType(VehicleType.CAR).pricePerHour(new BigDecimal("50000")).build());
        spotAId = spotA.getId();
        spotBId = spotB.getId();
        spotCId = spotC.getId();
        spotDId = spotD.getId();

        Vehicle vehicle = vehicleRepository.save(Vehicle.builder().userId(driver.getId()).licensePlate("51A-12345").vehicleType(VehicleType.CAR).active(true).build());
        vehicleId = vehicle.getId();

        // Spot A: CHECKED_IN now -> OCCUPIED
        reservationRepository.save(Reservation.builder()
                .spotId(spotAId).vehicleId(vehicleId).driverId(driver.getId())
                .status(ReservationStatus.CHECKED_IN)
                .startTime(LocalDateTime.now(clock).minusHours(1))
                .endTime(LocalDateTime.now(clock).plusHours(1))
                .checkedInAt(LocalDateTime.now(clock).minusHours(1))
                .totalPrice(new BigDecimal("100000"))
                .build());

        // Spot B: RESERVED now -> PENDING_BOOKING
        reservationRepository.save(Reservation.builder()
                .spotId(spotBId).vehicleId(vehicleId).driverId(driver.getId())
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now(clock).minusMinutes(10))
                .endTime(LocalDateTime.now(clock).plusMinutes(50))
                .totalPrice(new BigDecimal("50000"))
                .build());

        // Spot C: no active reservation -> AVAILABLE
        // Spot D: inactive -> UNAVAILABLE
    }

    @Test
    void getMap_correctStatuses_andPerformance() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(driverToken);

        long start = System.currentTimeMillis();
        ResponseEntity<ApiResponse<ParkingLotMapResponse>> response = restTemplate.exchange(
                "/api/parking-lots/" + lotId + "/map",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        long end = System.currentTimeMillis();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(end - start).isLessThan(200); // M3

        ParkingLotMapResponse map = response.getBody().data();
        assertThat(map.lotId()).isEqualTo(lotId);
        assertThat(map.spots()).hasSize(4);

        assertThat(map.spots().stream().filter(s -> s.id().equals(spotAId)).findFirst().get().status()).isEqualTo(SpotStatus.OCCUPIED);
        assertThat(map.spots().stream().filter(s -> s.id().equals(spotBId)).findFirst().get().status()).isEqualTo(SpotStatus.PENDING_BOOKING);
        assertThat(map.spots().stream().filter(s -> s.id().equals(spotCId)).findFirst().get().status()).isEqualTo(SpotStatus.AVAILABLE);
        assertThat(map.spots().stream().filter(s -> s.id().equals(spotDId)).findFirst().get().status()).isEqualTo(SpotStatus.UNAVAILABLE); // I3
    }

    @Test
    void getMap_cacheInvalidatedOnReservationCreate() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(driverToken);

        // 1. Initial call to fill cache
        restTemplate.exchange("/api/parking-lots/" + lotId + "/map", HttpMethod.GET, new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});
        assertThat(redisTemplate.hasKey("map:lot:" + lotId)).isTrue();

        // 2. Create reservation through API
        CreateReservationRequest request = new CreateReservationRequest(
                spotCId,
                vehicleId,
                LocalDateTime.now(clock).plusDays(1).withHour(10).withMinute(0),
                LocalDateTime.now(clock).plusDays(1).withHour(11).withMinute(0)
        );
        
        ResponseEntity<ApiResponse<Object>> createResponse = restTemplate.exchange(
                "/api/reservations",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // 3. Verify cache invalidated (I1)
        assertThat(redisTemplate.hasKey("map:lot:" + lotId)).isFalse();
    }
}
