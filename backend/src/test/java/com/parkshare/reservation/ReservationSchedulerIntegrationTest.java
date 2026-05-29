package com.parkshare.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.VehicleType;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
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
class ReservationSchedulerIntegrationTest {

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
    private Clock clock;

    @SpyBean
    private ReservationScheduler reservationScheduler;

    private UUID spotId;
    private UUID vehicleId;
    private UUID driverId;

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
        driverId = driver.getId();

        ParkingLot lot = parkingLotRepository.save(ParkingLot.builder().ownerId(owner.getId()).name("Lot").address("Addr").floor(1).verified(true).build());
        ParkingSpot spot = parkingSpotRepository.save(ParkingSpot.builder().lotId(lot.getId()).code("A1").vehicleType(VehicleType.CAR).pricePerHour(new BigDecimal("50000")).build());
        spotId = spot.getId();

        Vehicle vehicle = vehicleRepository.save(Vehicle.builder().userId(driver.getId()).licensePlate("51A-12345").vehicleType(VehicleType.CAR).build());
        vehicleId = vehicle.getId();
    }

    @Test
    void expireReservations_updatesCorrectRows() {
        // 1. RESERVED, startTime = 2 hours ago -> should -> EXPIRED
        Reservation r1 = reservationRepository.save(Reservation.builder()
                .spotId(spotId).vehicleId(vehicleId).driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now(clock).minusHours(2))
                .endTime(LocalDateTime.now(clock).minusHours(1))
                .totalPrice(new BigDecimal("50000"))
                .build());

        // 2. RESERVED, startTime = 30 min ago -> should stay RESERVED (< 45 min threshold)
        Reservation r2 = reservationRepository.save(Reservation.builder()
                .spotId(spotId).vehicleId(vehicleId).driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now(clock).minusMinutes(30))
                .endTime(LocalDateTime.now(clock).plusMinutes(30))
                .totalPrice(new BigDecimal("50000"))
                .build());

        reservationScheduler.expireReservations();

        assertThat(reservationRepository.findById(r1.getId()).get().getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(reservationRepository.findById(r2.getId()).get().getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void markNoShow_updatesCorrectRows() {
        // 1. CHECKED_IN, endTime = 2 hours ago -> should -> NO_SHOW
        Reservation r1 = reservationRepository.save(Reservation.builder()
                .spotId(spotId).vehicleId(vehicleId).driverId(driverId)
                .status(ReservationStatus.CHECKED_IN)
                .startTime(LocalDateTime.now(clock).minusHours(4))
                .endTime(LocalDateTime.now(clock).minusHours(2))
                .checkedInAt(LocalDateTime.now(clock).minusHours(4))
                .totalPrice(new BigDecimal("100000"))
                .build());

        // 2. CHECKED_IN, endTime = 30 min ago -> should stay CHECKED_IN (< 1 hour threshold)
        Reservation r2 = reservationRepository.save(Reservation.builder()
                .spotId(spotId).vehicleId(vehicleId).driverId(driverId)
                .status(ReservationStatus.CHECKED_IN)
                .startTime(LocalDateTime.now(clock).minusHours(1))
                .endTime(LocalDateTime.now(clock).minusMinutes(30))
                .checkedInAt(LocalDateTime.now(clock).minusHours(1))
                .totalPrice(new BigDecimal("50000"))
                .build());

        reservationScheduler.markNoShow();

        assertThat(reservationRepository.findById(r1.getId()).get().getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        assertThat(reservationRepository.findById(r2.getId()).get().getStatus()).isEqualTo(ReservationStatus.CHECKED_IN);
    }

    @Test
    void expireReservations_lockAlreadyHeld_skips() {
        // Pre-set the Redis lock key with a dummy token
        redisTemplate.opsForValue().set("lock:job:expireReservations", "dummy-token");

        Reservation r1 = reservationRepository.save(Reservation.builder()
                .spotId(spotId).vehicleId(vehicleId).driverId(driverId)
                .status(ReservationStatus.RESERVED)
                .startTime(LocalDateTime.now(clock).minusHours(2))
                .endTime(LocalDateTime.now(clock).minusHours(1))
                .totalPrice(new BigDecimal("50000"))
                .build());

        reservationScheduler.expireReservations();

        // Should still be RESERVED because job was skipped
        assertThat(reservationRepository.findById(r1.getId()).get().getStatus()).isEqualTo(ReservationStatus.RESERVED);
    }
}
