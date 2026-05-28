package com.parkshare.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.parkshare.parkingspot.VehicleType;
import com.parkshare.reservation.ReservationRepository;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import com.parkshare.vehicle.dto.CreateVehicleRequest;
import com.parkshare.vehicle.dto.UpdateVehicleRequest;
import com.parkshare.vehicle.dto.VehicleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Spy
    private VehicleMapper vehicleMapper = Mappers.getMapper(VehicleMapper.class);

    private VehicleService vehicleService;

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, vehicleMapper, reservationRepository);
    }

    @Test
    void createVehicle_success() {
        UUID userId = UUID.randomUUID();
        CreateVehicleRequest request = createVehicleRequest("51A-12345");
        when(vehicleRepository.existsByUserIdAndLicensePlateAndActiveTrue(userId, request.licensePlate())).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> {
            Vehicle v = invocation.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        VehicleResponse response = vehicleService.createVehicle(userId, request);

        assertThat(response.licensePlate()).isEqualTo("51A-12345");
    }

    @Test
    void createVehicle_duplicatePlate_throwsConflict() {
        UUID userId = UUID.randomUUID();
        CreateVehicleRequest request = createVehicleRequest("51A-12345");
        when(vehicleRepository.existsByUserIdAndLicensePlateAndActiveTrue(userId, request.licensePlate())).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.createVehicle(userId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This license plate is already registered");
    }

    @Test
    void getMyVehicles_returnsActiveVehiclesOnly() {
        UUID userId = UUID.randomUUID();
        when(vehicleRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of(
                Vehicle.builder().id(UUID.randomUUID()).userId(userId).licensePlate("51A-12345").active(true).build()
        ));

        List<VehicleResponse> responses = vehicleService.getMyVehicles(userId);

        assertThat(responses).hasSize(1);
    }

    @Test
    void updateVehicle_success() {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder().id(vehicleId).userId(userId).licensePlate("51A-11111").active(true).build();
        UpdateVehicleRequest request = updateVehicleRequest("51A-22222");

        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.existsByUserIdAndLicensePlateAndActiveTrueAndIdNot(userId, "51A-22222", vehicleId)).thenReturn(false);
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(vehicle);

        VehicleResponse response = vehicleService.updateVehicle(vehicleId, userId, request);

        assertThat(response.licensePlate()).isEqualTo("51A-22222");
    }

    @Test
    void updateVehicle_notFound_throws404() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.updateVehicle(vehicleId, UUID.randomUUID(), updateVehicleRequest("51A-12345")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateVehicle_notOwner_throwsForbidden() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder().id(vehicleId).userId(UUID.randomUUID()).active(true).build();
        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.updateVehicle(vehicleId, UUID.randomUUID(), updateVehicleRequest("51A-12345")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateVehicle_duplicatePlate_throwsConflict() {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder().id(vehicleId).userId(userId).licensePlate("51A-11111").active(true).build();
        UpdateVehicleRequest request = updateVehicleRequest("51A-22222");

        when(vehicleRepository.findByIdAndActiveTrue(vehicleId)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.existsByUserIdAndLicensePlateAndActiveTrueAndIdNot(userId, "51A-22222", vehicleId)).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.updateVehicle(vehicleId, userId, request))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteVehicle_success_setsActiveFalse() {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder().id(vehicleId).userId(userId).active(true).build();

        when(vehicleRepository.findByIdAndActiveTrueForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));

        vehicleService.deleteVehicle(vehicleId, userId);

        assertThat(vehicle.isActive()).isFalse();
        verify(vehicleRepository).save(vehicle);
    }

    @Test
    void deleteVehicle_notOwner_throwsForbidden() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder().id(vehicleId).userId(UUID.randomUUID()).active(true).build();
        when(vehicleRepository.findByIdAndActiveTrueForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.deleteVehicle(vehicleId, UUID.randomUUID()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteVehicle_notFound_throws404() {
        UUID vehicleId = UUID.randomUUID();
        when(vehicleRepository.findByIdAndActiveTrueForUpdate(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.deleteVehicle(vehicleId, UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void deleteVehicle_withActiveReservations_throws409() {
        UUID vehicleId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Vehicle vehicle = Vehicle.builder().id(vehicleId).userId(userId).active(true).build();
        when(vehicleRepository.findByIdAndActiveTrueForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));
        when(reservationRepository.existsByVehicleIdAndStatusIn(eq(vehicleId), any())).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.deleteVehicle(vehicleId, userId))
                .isInstanceOf(ConflictException.class);
    }

    private CreateVehicleRequest createVehicleRequest(String plate) {
        return new CreateVehicleRequest(plate, VehicleType.CAR, "Honda", "Civic", "Black");
    }

    private UpdateVehicleRequest updateVehicleRequest(String plate) {
        return new UpdateVehicleRequest(plate, VehicleType.CAR, "Honda", "Civic", "White");
    }
}
