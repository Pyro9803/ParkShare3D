package com.parkshare.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkshare.auth.jwt.JwtProperties;
import com.parkshare.auth.jwt.JwtProvider;
import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.DayOfWeek;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.SpotAvailability;
import com.parkshare.parkingspot.SpotAvailabilityRepository;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReservationConcurrencyTest {

    private static final String JWT_SECRET = "this-is-a-256-bit-dev-secret-key-do-not-use-in-prod!!";
    private static final int THREAD_COUNT = 10;

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.jwt.access-token-ttl-seconds", () -> "900");
        registry.add("app.jwt.refresh-token-ttl-seconds", () -> "604800");
    }

    @LocalServerPort
    private int port;

    @Autowired private UserRepository userRepository;
    @Autowired private ParkingLotRepository parkingLotRepository;
    @Autowired private ParkingSpotRepository parkingSpotRepository;
    @Autowired private SpotAvailabilityRepository spotAvailabilityRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final JwtProvider jwtProvider = new JwtProvider(new JwtProperties(JWT_SECRET, 900, 604800));

    private UUID spotId;
    private final List<String> driverTokens = new ArrayList<>();
    private final List<UUID> vehicleIds = new ArrayList<>();

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    private static final DayOfWeek BOOKING_DAY = DayOfWeek.from(TOMORROW.getDayOfWeek());
    private static final LocalDateTime BOOKING_START = TOMORROW.atTime(10, 0);
    private static final LocalDateTime BOOKING_END = TOMORROW.atTime(12, 0);

    @BeforeEach
    void seedData() {
        reservationRepository.deleteAll();
        vehicleRepository.deleteAll();
        spotAvailabilityRepository.deleteAll();
        parkingSpotRepository.deleteAll();
        parkingLotRepository.deleteAll();
        userRepository.deleteAll();
        driverTokens.clear();
        vehicleIds.clear();

        User owner = userRepository.save(User.builder()
                .email("owner-" + UUID.randomUUID() + "@test.com")
                .password(passwordEncoder.encode("pass"))
                .fullName("Owner")
                .role(UserRole.OWNER)
                .active(true)
                .build());

        ParkingLot lot = parkingLotRepository.save(ParkingLot.builder()
                .ownerId(owner.getId())
                .name("Test Lot")
                .address("123 Main St")
                .floor(1)
                .verified(true)
                .active(true)
                .build());

        ParkingSpot spot = parkingSpotRepository.save(ParkingSpot.builder()
                .lotId(lot.getId())
                .code("A-01")
                .x(0).y(0).z(0).width(2.5).length(5.0)
                .vehicleType(VehicleType.CAR)
                .pricePerHour(new BigDecimal("50000"))
                .active(true)
                .build());
        spotId = spot.getId();

        spotAvailabilityRepository.save(SpotAvailability.builder()
                .spotId(spotId)
                .dayOfWeek(BOOKING_DAY)
                .startTime(LocalTime.of(0, 0))
                .endTime(LocalTime.of(23, 59))
                .active(true)
                .build());

        for (int i = 0; i < THREAD_COUNT; i++) {
            User driver = userRepository.save(User.builder()
                    .email("driver-" + i + "-" + UUID.randomUUID() + "@test.com")
                    .password(passwordEncoder.encode("pass"))
                    .fullName("Driver " + i)
                    .role(UserRole.DRIVER)
                    .active(true)
                    .build());
            driverTokens.add(jwtProvider.generateAccessToken(driver.getId(), driver.getEmail(), driver.getRole()));

            Vehicle vehicle = vehicleRepository.save(Vehicle.builder()
                    .userId(driver.getId())
                    .licensePlate("51A-" + String.format("%05d", i))
                    .vehicleType(VehicleType.CAR)
                    .brand("Toyota")
                    .model("Camry")
                    .color("White")
                    .active(true)
                    .build());
            vehicleIds.add(vehicle.getId());
        }
    }

    @Test
    void concurrentBookings_onlyOneSucceeds() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    int status = postReservation(driverTokens.get(idx), spotId, vehicleIds.get(idx),
                            BOOKING_START, BOOKING_END, null);
                    if (status == 201) successCount.incrementAndGet();
                    else if (status == 409) conflictCount.incrementAndGet();
                    else failCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(failCount.get()).as("unexpected failures").isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(THREAD_COUNT - 1);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void create_idempotencyKey_sameKeyReturnsSameResponse() throws Exception {
        String key = UUID.randomUUID().toString();

        int status1 = postReservation(driverTokens.get(0), spotId, vehicleIds.get(0), BOOKING_START, BOOKING_END, key);
        int status2 = postReservation(driverTokens.get(0), spotId, vehicleIds.get(0), BOOKING_START, BOOKING_END, key);

        assertThat(status1).isEqualTo(201);
        assertThat(status2).isEqualTo(201);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    @Test
    void create_vehicleTypeMismatch_returns400() throws Exception {
        ParkingSpot spot = parkingSpotRepository.findById(spotId).orElseThrow();
        spot.setVehicleType(VehicleType.MOTORBIKE);
        parkingSpotRepository.save(spot);

        int status = postReservation(driverTokens.get(0), spotId, vehicleIds.get(0), BOOKING_START, BOOKING_END, null);

        assertThat(status).isEqualTo(400);
    }

    @Test
    void create_driverIsLotOwner_returns403() throws Exception {
        User driverUser = userRepository.findByEmail(extractEmail(driverTokens.get(0))).orElseThrow();

        ParkingLot ownedLot = parkingLotRepository.save(ParkingLot.builder()
                .ownerId(driverUser.getId())
                .name("Driver's Lot")
                .address("456 Self St")
                .floor(1)
                .verified(true)
                .active(true)
                .build());

        ParkingSpot ownedSpot = parkingSpotRepository.save(ParkingSpot.builder()
                .lotId(ownedLot.getId())
                .code("B-01")
                .x(0).y(0).z(0).width(2.5).length(5.0)
                .vehicleType(VehicleType.CAR)
                .pricePerHour(new BigDecimal("50000"))
                .active(true)
                .build());

        spotAvailabilityRepository.save(SpotAvailability.builder()
                .spotId(ownedSpot.getId())
                .dayOfWeek(BOOKING_DAY)
                .startTime(LocalTime.of(0, 0))
                .endTime(LocalTime.of(23, 59))
                .active(true)
                .build());

        int status = postReservation(driverTokens.get(0), ownedSpot.getId(), vehicleIds.get(0),
                BOOKING_START, BOOKING_END, null);

        assertThat(status).isEqualTo(403);
    }

    private int postReservation(String token, UUID spotId, UUID vehicleId,
                                 LocalDateTime start, LocalDateTime end, String idempotencyKey) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "parkingSpotId", spotId.toString(),
                "vehicleId", vehicleId.toString(),
                "startTime", start.toString(),
                "endTime", end.toString()
        ));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url("/api/reservations")))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private String extractEmail(String token) {
        try {
            String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
            return objectMapper.readTree(payload).get("sub").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
