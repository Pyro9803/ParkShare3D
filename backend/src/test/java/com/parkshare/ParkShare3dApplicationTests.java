package com.parkshare;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.user.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
class ParkShare3dApplicationTests {

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ParkingLotRepository parkingLotRepository;

    @MockBean
    private ParkingSpotRepository parkingSpotRepository;

    @MockBean
    private com.parkshare.parkingspot.SpotAvailabilityRepository spotAvailabilityRepository;

    @MockBean
    private com.parkshare.vehicle.VehicleRepository vehicleRepository;

    @MockBean
    private ReservationRepository reservationRepository;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void contextLoads() {
    }
}
