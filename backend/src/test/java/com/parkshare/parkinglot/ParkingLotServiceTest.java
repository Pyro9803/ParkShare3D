package com.parkshare.parkinglot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.parkshare.parkinglot.dto.CreateLotRequest;
import com.parkshare.parkinglot.dto.CreateSpotRequest;
import com.parkshare.parkinglot.dto.LotDetailResponse;
import com.parkshare.parkinglot.dto.LotResponse;
import com.parkshare.parkinglot.dto.UpdateLotRequest;
import com.parkshare.parkinglot.dto.UpdateSpotRequest;
import com.parkshare.parkingspot.ParkingSpot;
import com.parkshare.parkingspot.ParkingSpotRepository;
import com.parkshare.parkingspot.VehicleType;
import com.parkshare.parkingspot.dto.SpotResponse;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ParkingLotServiceTest {

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    private ParkingLotService parkingLotService;

    @BeforeEach
    void setUp() {
        parkingLotService = new ParkingLotService(parkingLotRepository, parkingSpotRepository);
    }

    @Test
    void createLotSavesLotAndSpots() {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        CreateLotRequest request = new CreateLotRequest(
                "Central Garage",
                "123 Main St",
                "Covered parking",
                3,
                List.of(createSpotRequest("A-01"))
        );
        ParkingLot savedLot = lot(lotId, ownerId);
        when(parkingLotRepository.save(any(ParkingLot.class))).thenReturn(savedLot);
        when(parkingSpotRepository.existsByLotIdAndCodeAndActiveTrue(lotId, "A-01")).thenReturn(false);
        when(parkingSpotRepository.save(any(ParkingSpot.class))).thenReturn(spot(spotId, lotId, "A-01"));

        LotDetailResponse response = parkingLotService.createLot(ownerId, request);

        assertThat(response.id()).isEqualTo(lotId);
        assertThat(response.ownerId()).isEqualTo(ownerId);
        assertThat(response.name()).isEqualTo("Central Garage");
        assertThat(response.spots()).hasSize(1);
        assertThat(response.spots().get(0).code()).isEqualTo("A-01");
        ArgumentCaptor<ParkingLot> lotCaptor = ArgumentCaptor.forClass(ParkingLot.class);
        verify(parkingLotRepository).save(lotCaptor.capture());
        assertThat(lotCaptor.getValue().getOwnerId()).isEqualTo(ownerId);
        verify(parkingSpotRepository).save(any(ParkingSpot.class));
    }

    @Test
    void createLotRejectsDuplicateSpotCode() {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        CreateLotRequest request = new CreateLotRequest(
                "Central Garage",
                "123 Main St",
                null,
                2,
                List.of(createSpotRequest("A-01"), createSpotRequest("A-01"))
        );
        when(parkingLotRepository.save(any(ParkingLot.class))).thenReturn(lot(lotId, ownerId));
        when(parkingSpotRepository.existsByLotIdAndCodeAndActiveTrue(lotId, "A-01")).thenReturn(false, false);
        when(parkingSpotRepository.save(any(ParkingSpot.class))).thenReturn(spot(UUID.randomUUID(), lotId, "A-01"));

        assertThatThrownBy(() -> parkingLotService.createLot(ownerId, request))
                .isInstanceOfSatisfying(ConflictException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("SPOT_CODE_DUPLICATE");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void getLotRejectsMissingLot() {
        UUID lotId = UUID.randomUUID();
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parkingLotService.getLot(lotId))
                .isInstanceOfSatisfying(EntityNotFoundException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("LOT_NOT_FOUND"));
    }

    @Test
    void getLotRejectsInactiveLot() {
        UUID lotId = UUID.randomUUID();
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parkingLotService.getLot(lotId))
                .isInstanceOfSatisfying(EntityNotFoundException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("LOT_NOT_FOUND"));
    }

    @Test
    void updateLotRejectsNonOwner() {
        UUID lotId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot(lotId, ownerId)));

        assertThatThrownBy(() -> parkingLotService.updateLot(lotId, callerId, updateLotRequest()))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("NOT_LOT_OWNER");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        verify(parkingLotRepository, never()).save(any());
    }

    @Test
    void updateLotSavesChangedFields() {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        ParkingLot lot = lot(lotId, ownerId);
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));
        when(parkingLotRepository.save(lot)).thenReturn(lot);

        LotResponse response = parkingLotService.updateLot(lotId, ownerId, updateLotRequest());

        assertThat(response.name()).isEqualTo("Updated Garage");
        assertThat(response.address()).isEqualTo("456 Updated Ave");
        assertThat(response.floor()).isEqualTo(4);
        verify(parkingLotRepository).save(lot);
    }

    @Test
    void addSpotRejectsDuplicateCode() {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot(lotId, ownerId)));
        when(parkingSpotRepository.existsByLotIdAndCodeAndActiveTrue(lotId, "A-01")).thenReturn(true);

        assertThatThrownBy(() -> parkingLotService.addSpot(lotId, ownerId, createSpotRequest("A-01")))
                .isInstanceOfSatisfying(ConflictException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SPOT_CODE_DUPLICATE"));
        verify(parkingSpotRepository, never()).save(any());
    }

    @Test
    void addSpotAllowsCodeFromSoftDeletedSpot() {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot(lotId, ownerId)));
        when(parkingSpotRepository.existsByLotIdAndCodeAndActiveTrue(lotId, "A-01")).thenReturn(false);
        when(parkingSpotRepository.save(any(ParkingSpot.class))).thenReturn(spot(spotId, lotId, "A-01"));

        SpotResponse response = parkingLotService.addSpot(lotId, ownerId, createSpotRequest("A-01"));

        assertThat(response.id()).isEqualTo(spotId);
        assertThat(response.code()).isEqualTo("A-01");
        verify(parkingSpotRepository).save(any(ParkingSpot.class));
    }

    @Test
    void deleteSpotRejectsNonOwner() {
        UUID lotId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        ParkingSpot spot = spot(spotId, lotId, "A-01");
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot(lotId, ownerId)));

        assertThatThrownBy(() -> parkingLotService.deleteSpot(spotId, callerId))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("NOT_LOT_OWNER");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        verify(parkingSpotRepository, never()).save(any());
    }

    @Test
    void deleteSpotSoftDeletesActiveSpot() {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        ParkingSpot spot = spot(spotId, lotId, "A-01");
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot(lotId, ownerId)));

        parkingLotService.deleteSpot(spotId, ownerId);

        assertThat(spot.isActive()).isFalse();
        verify(parkingSpotRepository).save(spot);
    }

    @Test
    void verifyLotMarksLotVerified() {
        UUID lotId = UUID.randomUUID();
        ParkingLot lot = lot(lotId, UUID.randomUUID());
        lot.setVerified(false);
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));
        when(parkingLotRepository.save(lot)).thenReturn(lot);

        LotResponse response = parkingLotService.verifyLot(lotId);

        assertThat(response.verified()).isTrue();
        verify(parkingLotRepository).save(lot);
    }

    @Test
    void updateSpotRejectsDuplicateCodeFromAnotherSpot() {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        ParkingSpot spot = spot(spotId, lotId, "A-01");
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot(lotId, ownerId)));
        when(parkingSpotRepository.existsByLotIdAndCodeAndActiveTrueAndIdNot(lotId, "A-02", spotId)).thenReturn(true);

        assertThatThrownBy(() -> parkingLotService.updateSpot(spotId, ownerId, updateSpotRequest("A-02")))
                .isInstanceOfSatisfying(ConflictException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SPOT_CODE_DUPLICATE"));
        verify(parkingSpotRepository, never()).save(any());
    }

    @Test
    void updateSpotRejectsNonOwner() {
        UUID lotId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        ParkingSpot spot = spot(spotId, lotId, "A-01");
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot(lotId, ownerId)));

        assertThatThrownBy(() -> parkingLotService.updateSpot(spotId, callerId, updateSpotRequest("A-02")))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("NOT_LOT_OWNER");
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        verify(parkingSpotRepository, never()).save(any());
    }

    @Test
    void updateSpotSavesChangedFields() {
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        UUID spotId = UUID.randomUUID();
        ParkingSpot spot = spot(spotId, lotId, "A-01");
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot(lotId, ownerId)));
        when(parkingSpotRepository.existsByLotIdAndCodeAndActiveTrueAndIdNot(lotId, "A-02", spotId)).thenReturn(false);
        when(parkingSpotRepository.save(spot)).thenReturn(spot);

        SpotResponse response = parkingLotService.updateSpot(spotId, ownerId, updateSpotRequest("A-02"));

        assertThat(response.code()).isEqualTo("A-02");
        assertThat(response.pricePerHour()).isEqualByComparingTo("3.50");
        verify(parkingSpotRepository).save(spot);
    }

    @Test
    void updateSpotRejectsInactiveSpot() {
        UUID spotId = UUID.randomUUID();
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parkingLotService.updateSpot(spotId, UUID.randomUUID(), updateSpotRequest("A-02")))
                .isInstanceOfSatisfying(EntityNotFoundException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SPOT_NOT_FOUND"));
    }

    @Test
    void deleteSpotRejectsInactiveSpot() {
        UUID spotId = UUID.randomUUID();
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> parkingLotService.deleteSpot(spotId, UUID.randomUUID()))
                .isInstanceOfSatisfying(EntityNotFoundException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("SPOT_NOT_FOUND"));
    }

    private static CreateSpotRequest createSpotRequest(String code) {
        return new CreateSpotRequest(
                code,
                1.0,
                2.0,
                3.0,
                2.5,
                5.0,
                VehicleType.CAR,
                new BigDecimal("2.75")
        );
    }

    private static UpdateSpotRequest updateSpotRequest(String code) {
        return new UpdateSpotRequest(
                code,
                5.0,
                6.0,
                7.0,
                3.0,
                5.5,
                VehicleType.TRUCK,
                new BigDecimal("3.50")
        );
    }

    private static UpdateLotRequest updateLotRequest() {
        return new UpdateLotRequest("Updated Garage", "456 Updated Ave", "Updated", 4);
    }

    private static ParkingLot lot(UUID lotId, UUID ownerId) {
        return ParkingLot.builder()
                .id(lotId)
                .ownerId(ownerId)
                .name("Central Garage")
                .address("123 Main St")
                .description("Covered parking")
                .floor(3)
                .verified(false)
                .active(true)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
    }

    private static ParkingSpot spot(UUID spotId, UUID lotId, String code) {
        return ParkingSpot.builder()
                .id(spotId)
                .lotId(lotId)
                .code(code)
                .x(1.0)
                .y(2.0)
                .z(3.0)
                .width(2.5)
                .length(5.0)
                .vehicleType(VehicleType.CAR)
                .pricePerHour(new BigDecimal("2.75"))
                .active(true)
                .build();
    }
}
