package com.uberpoc.matching.dto.request;

import com.uberpoc.matching.domain.DriverResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class DriverRespondDto {

    @NotBlank
    private String driverId;

    @NotNull
    private DriverResponse response;
}
