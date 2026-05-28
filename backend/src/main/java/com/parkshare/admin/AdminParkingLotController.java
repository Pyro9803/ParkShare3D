package com.parkshare.admin;

import java.util.UUID;

import com.parkshare.parkinglot.ParkingLotService;
import com.parkshare.parkinglot.dto.LotResponse;
import com.parkshare.shared.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/parking-lots")
public class AdminParkingLotController {

    private final ParkingLotService parkingLotService;

    public AdminParkingLotController(ParkingLotService parkingLotService) {
        this.parkingLotService = parkingLotService;
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<LotResponse> verifyLot(@PathVariable UUID id) {
        return ApiResponse.success(parkingLotService.verifyLot(id));
    }
}
