package com.parkshare.parkingspot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.parkshare.parkinglot.ParkingLot;
import com.parkshare.parkinglot.ParkingLotRepository;
import com.parkshare.parkingspot.dto.AvailabilityResponse;
import com.parkshare.parkingspot.dto.CreateAvailabilityRequest;
import com.parkshare.parkingspot.dto.ReplaceAvailabilityRequest;
import com.parkshare.shared.exception.BusinessException;
import com.parkshare.shared.exception.ConflictException;
import com.parkshare.shared.exception.EntityNotFoundException;
import com.parkshare.shared.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpotAvailabilityServiceTest {

    @Mock
    private SpotAvailabilityRepository spotAvailabilityRepository;

    @Mock
    private ParkingSpotRepository parkingSpotRepository;

    @Mock
    private ParkingLotRepository parkingLotRepository;

    private SpotAvailabilityService spotAvailabilityService;

    @BeforeEach
    void setUp() {
        spotAvailabilityService = new SpotAvailabilityService(
                spotAvailabilityRepository,
                parkingSpotRepository,
                parkingLotRepository
        );
    }

    @Test
    void replaceAvailability_success() {
        UUID spotId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        ParkingSpot spot = ParkingSpot.builder().id(spotId).lotId(lotId).active(true).build();
        ParkingLot lot = ParkingLot.builder().id(lotId).ownerId(callerId).active(true).build();

        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));
        when(spotAvailabilityRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ReplaceAvailabilityRequest request = new ReplaceAvailabilityRequest(List.of(
                new CreateAvailabilityRequest(DayOfWeek.MON, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new CreateAvailabilityRequest(DayOfWeek.MON, LocalTime.of(13, 0), LocalTime.of(17, 0))
        ));

        List<AvailabilityResponse> responses = spotAvailabilityService.replaceAvailability(spotId, callerId, request);

        assertThat(responses).hasSize(2);
        verify(spotAvailabilityRepository).softDeleteAllBySpotId(spotId);
        verify(spotAvailabilityRepository).saveAll(anyList());
    }

    @Test
    void replaceAvailability_spotNotFound_throws404() {
        UUID spotId = UUID.randomUUID();
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spotAvailabilityService.replaceAvailability(spotId, UUID.randomUUID(), new ReplaceAvailabilityRequest(List.of())))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Parking spot not found");
    }

    @Test
    void replaceAvailability_notOwner_throwsForbidden() {
        UUID spotId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        ParkingSpot spot = ParkingSpot.builder().id(spotId).lotId(lotId).active(true).build();
        ParkingLot lot = ParkingLot.builder().id(lotId).ownerId(ownerId).active(true).build();

        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> spotAvailabilityService.replaceAvailability(spotId, callerId, new ReplaceAvailabilityRequest(List.of())))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only the parking lot owner can modify this resource");
    }

    @Test
    void replaceAvailability_invalidTimeRange_throws400() {
        UUID spotId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        ParkingSpot spot = ParkingSpot.builder().id(spotId).lotId(lotId).active(true).build();
        ParkingLot lot = ParkingLot.builder().id(lotId).ownerId(callerId).active(true).build();

        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));

        ReplaceAvailabilityRequest request = new ReplaceAvailabilityRequest(List.of(
                new CreateAvailabilityRequest(DayOfWeek.MON, LocalTime.of(17, 0), LocalTime.of(8, 0))
        ));

        assertThatThrownBy(() -> spotAvailabilityService.replaceAvailability(spotId, callerId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Start time must be before end time");
    }

    @Test
    void replaceAvailability_overlappingSlots_sameDay_throws409() {
        UUID spotId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        ParkingSpot spot = ParkingSpot.builder().id(spotId).lotId(lotId).active(true).build();
        ParkingLot lot = ParkingLot.builder().id(lotId).ownerId(callerId).active(true).build();

        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));

        ReplaceAvailabilityRequest request = new ReplaceAvailabilityRequest(List.of(
                new CreateAvailabilityRequest(DayOfWeek.MON, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new CreateAvailabilityRequest(DayOfWeek.MON, LocalTime.of(11, 0), LocalTime.of(13, 0))
        ));

        assertThatThrownBy(() -> spotAvailabilityService.replaceAvailability(spotId, callerId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Schedule slots overlap for the same day");
    }

    @Test
    void getAvailability_returnsActiveSlots() {
        UUID spotId = UUID.randomUUID();
        ParkingSpot spot = ParkingSpot.builder().id(spotId).active(true).build();
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(spotAvailabilityRepository.findAllBySpotIdAndActiveTrue(spotId)).thenReturn(List.of(
                SpotAvailability.builder().id(UUID.randomUUID()).spotId(spotId).dayOfWeek(DayOfWeek.MON).startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(12, 0)).active(true).build()
        ));

        List<AvailabilityResponse> responses = spotAvailabilityService.getAvailability(spotId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).dayOfWeek()).isEqualTo(DayOfWeek.MON);
    }

    @Test
    void deleteAvailability_success() {
        UUID spotId = UUID.randomUUID();
        UUID availabilityId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID lotId = UUID.randomUUID();
        ParkingSpot spot = ParkingSpot.builder().id(spotId).lotId(lotId).active(true).build();
        ParkingLot lot = ParkingLot.builder().id(lotId).ownerId(callerId).active(true).build();
        SpotAvailability sa = SpotAvailability.builder().id(availabilityId).spotId(spotId).active(true).build();

        when(spotAvailabilityRepository.findByIdAndActiveTrue(availabilityId)).thenReturn(Optional.of(sa));
        when(parkingSpotRepository.findByIdAndActiveTrue(spotId)).thenReturn(Optional.of(spot));
        when(parkingLotRepository.findByIdAndActiveTrue(lotId)).thenReturn(Optional.of(lot));

        spotAvailabilityService.deleteAvailability(spotId, availabilityId, callerId);

        assertThat(sa.isActive()).isFalse();
        verify(spotAvailabilityRepository).save(sa);
    }

    @Test
    void deleteAvailability_notFound_throws404() {
        UUID availabilityId = UUID.randomUUID();
        when(spotAvailabilityRepository.findByIdAndActiveTrue(availabilityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spotAvailabilityService.deleteAvailability(UUID.randomUUID(), availabilityId, UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Availability slot not found");
    }

    @Test
    void deleteAvailability_slotBelongsToDifferentSpot_throws404() {
        UUID spotId = UUID.randomUUID();
        UUID otherSpotId = UUID.randomUUID();
        UUID availabilityId = UUID.randomUUID();
        SpotAvailability sa = SpotAvailability.builder().id(availabilityId).spotId(otherSpotId).active(true).build();

        when(spotAvailabilityRepository.findByIdAndActiveTrue(availabilityId)).thenReturn(Optional.of(sa));

        assertThatThrownBy(() -> spotAvailabilityService.deleteAvailability(spotId, availabilityId, UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Availability slot not found");
    }
}
