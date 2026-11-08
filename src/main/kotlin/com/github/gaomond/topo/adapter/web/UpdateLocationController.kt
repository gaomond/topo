package com.github.gaomond.topo.adapter.web

import com.github.gaomond.topo.usecase.UpdateLiveLocationInput
import com.github.gaomond.topo.usecase.UpdateLiveLocationUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** 成功時は 204 No Content（ボディなし）。 */
@RestController
class UpdateLocationController(
    private val updateLiveLocationUseCase: UpdateLiveLocationUseCase,
) {
    @PutMapping("/api/games/{id}/players/{pid}/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updateLocation(
        @PathVariable id: UUID,
        @PathVariable pid: UUID,
        @RequestBody request: UpdateLocationRequest,
    ) {
        updateLiveLocationUseCase.update(UpdateLiveLocationInput(id, pid, request.lat, request.lng))
    }
}
