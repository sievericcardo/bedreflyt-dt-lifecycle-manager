package no.uio.bedreflyt.lm.controller

import io.swagger.v3.oas.annotations.Parameter
import no.uio.bedreflyt.lm.service.StateService
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.logging.Logger
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import no.uio.bedreflyt.lm.types.WardState
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@RestController
@RequestMapping("/api/v1/states")
class StateController (
    private val stateService: StateService
) {

    private val log: Logger = Logger.getLogger(StateController::class.java.name)

    @Operation(summary = "Get the state of the ward")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "State retrieved"),
        ApiResponse(responseCode = "400", description = "Invalid request"),
        ApiResponse(responseCode = "401", description = "Unauthorized"),
        ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
        ApiResponse(responseCode = "500", description = "Internal server error")
    ])
    @GetMapping("/{wardName}/{hospitalCode}", produces = ["application/json"])
    fun getState(
        @Parameter(description = "Ward name", required = true) @PathVariable wardName: String,
        @Parameter(description = "Hospital code", required = true) @PathVariable hospitalCode: String
    ) : ResponseEntity<WardState> {
        log.info("Getting state of ward $wardName in hospital $hospitalCode")

        val state = stateService.getState(wardName, hospitalCode) ?: return ResponseEntity.badRequest().build()

        return ResponseEntity.ok(state)
    }
}